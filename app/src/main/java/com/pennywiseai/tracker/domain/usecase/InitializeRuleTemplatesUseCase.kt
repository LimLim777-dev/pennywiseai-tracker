package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleTemplateService
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class InitializeRuleTemplatesUseCase @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val ruleTemplateService: RuleTemplateService
) {
    suspend operator fun invoke(
        forceReset: Boolean = false,
        deletedSystemTemplateIds: Set<String> = emptySet()
    ) {
        if (forceReset) {
            ruleRepository.deleteAllRules()
        }

        val existingIds = ruleRepository.getAllRules().first().map { it.id }.toSet()
        val templates = ruleTemplateService.getDefaultRuleTemplates()
        templates.forEach { template ->
            if (template.id !in existingIds && template.id !in deletedSystemTemplateIds) {
                ruleRepository.insertRule(template)
            }
        }
    }
}