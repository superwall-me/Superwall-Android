package com.superwall.sdk.config

import com.superwall.sdk.models.assignment.Assignment
import com.superwall.sdk.models.assignment.ConfirmableAssignment
import com.superwall.sdk.models.config.Config
import com.superwall.sdk.models.config.PreloadingDisabled
import com.superwall.sdk.models.paywall.Paywall
import com.superwall.sdk.models.triggers.*
import java.util.*

object ConfigLogic {
    sealed class TriggerRuleError : Exception() {
        object NoVariantsFound : TriggerRuleError()
        object InvalidState : TriggerRuleError()
    }

    data class AssignmentOutcome(
        val confirmed: Map<ExperimentID, Experiment.Variant>,
        val unconfirmed: Map<ExperimentID, Experiment.Variant>
    )

    @Throws(TriggerRuleError::class)
    fun chooseVariant(
        variants: List<VariantOption>,
        randomiser: (IntRange) -> Int = { it.random() }
    ): Experiment.Variant {
        if (variants.isEmpty()) {
            throw TriggerRuleError.NoVariantsFound
        }

        if (variants.size == 1) {
            val variant = variants.first()
            return Experiment.Variant(
                id = variant.id,
                type = variant.type,
                paywallId = variant.paywallId
            )
        }

        val variantSum = variants.sumOf { it.percentage }

        if (variantSum == 0) {
            val randomVariantIndex = randomiser(0 until variants.size)
            val variant = variants[randomVariantIndex]
            return Experiment.Variant(
                id = variant.id,
                type = variant.type,
                paywallId = variant.paywallId
            )
        }

        val randomPercentage = randomiser(0 until variantSum)
        val normRandomPercentage = randomPercentage.toDouble() / variantSum.toDouble()

        var totalNormVariantPercentage = 0.0

        for (variant in variants) {
            val normVariantPercentage = variant.percentage.toDouble() / variantSum.toDouble()
            totalNormVariantPercentage += normVariantPercentage

            if (normRandomPercentage < totalNormVariantPercentage) {
                return Experiment.Variant(
                    id = variant.id,
                    type = variant.type,
                    paywallId = variant.paywallId
                )
            }
        }

        throw TriggerRuleError.InvalidState
    }

    fun getRulesPerTriggerGroup(triggers: Set<Trigger>): Set<List<TriggerRule>> {
        val groupIds: MutableSet<String> = mutableSetOf()
        val groupedTriggerRules: MutableSet<List<TriggerRule>> = mutableSetOf()
        for (trigger in triggers) {
            val firstRule = trigger.rules.firstOrNull() ?: continue
            val groupId = firstRule.experiment.groupId

            if (groupIds.contains(groupId)) {
                continue
            }

            groupIds.add(groupId)
            groupedTriggerRules.add(trigger.rules)
        }
        println("!!! groupedTriggerRules")
        println(groupedTriggerRules)
        return groupedTriggerRules
    }

    // Rest of the methods are converted similarly...
    fun chooseAssignments(
        fromTriggers: Set<Trigger>,
        confirmedAssignments: Map<ExperimentID, Experiment.Variant>
    ): AssignmentOutcome {
        var confirmedAssignments = confirmedAssignments.toMutableMap()
        var unconfirmedAssignments: MutableMap<ExperimentID, Experiment.Variant> = mutableMapOf()

        val groupedTriggerRules = getRulesPerTriggerGroup(fromTriggers)

        for (ruleGroup in groupedTriggerRules) {
            for (rule in ruleGroup) {
                val availableVariantIds = rule.experiment.variants.map { it.id }.toSet()

                val confirmedVariant = confirmedAssignments[rule.experiment.id]
                if (confirmedVariant != null) {
                    if (!availableVariantIds.contains(confirmedVariant.id)) {
                        val variant = try {
                            chooseVariant(rule.experiment.variants)
                        } catch (e: TriggerRuleError) {
                            confirmedAssignments.remove(rule.experiment.id)
                            continue
                        }
                        unconfirmedAssignments[rule.experiment.id] = variant
                        confirmedAssignments.remove(rule.experiment.id)
                    }
                } else {
                    val variant = try {
                        chooseVariant(rule.experiment.variants)
                    } catch (e: TriggerRuleError) {
                        continue
                    }
                    unconfirmedAssignments[rule.experiment.id] = variant
                }
            }
        }

        return AssignmentOutcome(
            confirmed = confirmedAssignments,
            unconfirmed = unconfirmedAssignments
        )
    }


    fun move(
        newAssignment: ConfirmableAssignment,
        unconfirmedAssignments: Map<ExperimentID, Experiment.Variant>,
        confirmedAssignments: Map<ExperimentID, Experiment.Variant>
    ): AssignmentOutcome {
        val confirmedAssignments = confirmedAssignments.toMutableMap()
        confirmedAssignments[newAssignment.experimentId] = newAssignment.variant

        val unconfirmedAssignments = unconfirmedAssignments.toMutableMap()
        unconfirmedAssignments.remove(newAssignment.experimentId)

        return AssignmentOutcome(
            confirmed = confirmedAssignments,
            unconfirmed = unconfirmedAssignments
        )
    }


    fun filterTriggers(
        triggers: Set<Trigger>,
        preloadingDisabled: PreloadingDisabled
    ): Set<Trigger> {
        return if (preloadingDisabled.all) {
            emptySet()
        } else {
            triggers.filterNot { preloadingDisabled.triggers.contains(it.eventName) }.toSet()
        }
    }

    fun transferAssignmentsFromServerToDisk(
        assignments: List<Assignment>,
        triggers: Set<Trigger>,
        confirmedAssignments: Map<ExperimentID, Experiment.Variant>,
        unconfirmedAssignments: Map<ExperimentID, Experiment.Variant>
    ): AssignmentOutcome {
        var confirmedAssignments = confirmedAssignments.toMutableMap()
        var unconfirmedAssignments = unconfirmedAssignments.toMutableMap()
        for (assignment in assignments) {
            val trigger = triggers.firstOrNull {
                it.rules.any { rule -> rule.experiment.id == assignment.experimentId }
            } ?: continue

            val variantOption = trigger.rules.flatMap {
                it.experiment.variants.filter { variant -> variant.id == assignment.variantId }
            }.firstOrNull() ?: continue

            confirmedAssignments[assignment.experimentId] = variantOption.toVariant()
            unconfirmedAssignments.remove(assignment.experimentId)
        }

        return AssignmentOutcome(
            confirmed = confirmedAssignments,
            unconfirmed = unconfirmedAssignments
        )
    }

    fun getStaticPaywall(
        withId: String?,
        config: Config?,
        deviceLocale: String
    ): Paywall? {
        val paywallId = withId
        if (paywallId == null || config == null) return null

        return if (config.locales.contains(deviceLocale)) {
            null
        } else {
            val shortLocale = deviceLocale.split("_").firstOrNull() ?: return null
            if (shortLocale == "en" || !config.locales.contains(shortLocale)) {
                config.paywalls.firstOrNull { it.identifier == paywallId }
            } else {
                null
            }
        }
    }

    fun getAllActiveTreatmentPaywallIds(
        fromTriggers: Set<Trigger>,
        confirmedAssignments: Map<ExperimentID, Experiment.Variant>,
        unconfirmedAssignments: Map<ExperimentID, Experiment.Variant>
    ): Set<String> {
        val triggers = fromTriggers
        var confirmedAssignments = confirmedAssignments.toMutableMap()

        val confirmedExperimentIds = confirmedAssignments.keys.toSet()
        val groupedTriggerRules = getRulesPerTriggerGroup(triggers)
        val triggerExperimentIds =
            groupedTriggerRules.flatMap { it.map { rule -> rule.experiment.id } }.toSet()
        val oldExperimentIds = confirmedExperimentIds - triggerExperimentIds

        for (id in oldExperimentIds) {
            confirmedAssignments.remove(id)
        }

        val confirmedVariants = confirmedAssignments.values.toList()
        val unconfirmedVariants = unconfirmedAssignments.values.toList()
        val mergedVariants = confirmedVariants + unconfirmedVariants
        val identifiers = mutableSetOf<String>()

        for (variant in mergedVariants) {
            if (variant.type == Experiment.Variant.VariantType.TREATMENT && variant.paywallId != null) {
                identifiers.add(variant.paywallId)
            }
        }

        return identifiers
    }

    fun getActiveTreatmentPaywallIds(
        forTriggers: Set<Trigger>,
        confirmedAssignments: Map<ExperimentID, Experiment.Variant>,
        unconfirmedAssignments: Map<ExperimentID, Experiment.Variant>
    ): Set<String> {
        val triggers = forTriggers
        val mergedAssignments = confirmedAssignments + unconfirmedAssignments
        val groupedTriggerRules = getRulesPerTriggerGroup(triggers)
        val triggerExperimentIds = groupedTriggerRules.flatMap { it.map { it.experiment.id } }

        val identifiers = mutableSetOf<String>()
        for (experimentId in triggerExperimentIds) {
            val variant = mergedAssignments[experimentId] ?: continue
            if (variant.type == Experiment.Variant.VariantType.TREATMENT) {
                variant.paywallId?.let {
                    identifiers.add(it)
                }
            }
        }
        return identifiers
    }

    fun getTriggersByEventName(from: Set<Trigger>): Map<String, Trigger> {
        val triggers = from
        return triggers.associateBy { it.eventName }
    }
}
