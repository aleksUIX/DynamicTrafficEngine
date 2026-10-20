// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.experiment;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.EvaluationContext;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentDefinition;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ConfigurationProvider;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.log4j.Log4j2;

/**
 * Manages the setup and allocation of experiments and treatments.
 * <p>
 * This class coordinates the process of setting up experiment contexts, allocating
 * treatments to experiments, and handling experiment configurations. It uses a
 * configuration provider, a treatment allocator, and an experiment handler to
 * perform these tasks.
 * </p>
 */
@Log4j2
public class ExperimentManager {

    private final ConfigurationProvider<ExperimentConfiguration> provider;
    private final TreatmentAllocator allocator;

    /**
     * @param handler not used. Each request builds its own arrangement, so one
     *                request's ExperimentContext never changes when another
     *                request is set up.
     */
    public ExperimentManager(
            ConfigurationProvider<ExperimentConfiguration> experimentConfigurationProvider,
            TreatmentAllocator allocator,
            ExperimentHandler handler
    ) {
        this.provider = experimentConfigurationProvider;
        this.allocator = allocator;
    }

    /**
     * Sets up the experiment context for a given evaluation context.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Retrieves the current experiment configuration</li>
     *   <li>For each experiment in the configuration, allocates a treatment using the allocator</li>
     *   <li>Records the allocated treatments in a map owned by this request</li>
     *   <li>Creates and sets an ExperimentContext in the provided EvaluationContext</li>
     * </ol>
     * If an error occurs during this process, it adds an error message to the context
     * and throws an IllegalStateException.
     * </p>
     *
     * @param context The EvaluationContext to set up the experiment context for.
     */
    public void setupExperimentContext(EvaluationContext context) {
        try {
            ExperimentConfiguration experimentConfiguration = provider.provide();
            log.debug("experimentConfiguration: {}", experimentConfiguration);
            Map<String, String> arrangement = new HashMap<>();
            for (Map.Entry<String, ExperimentDefinition> entry : experimentConfiguration.getExperimentDefinitionByName().entrySet()) {
                String treatmentCode = allocator.getTreatmentCode(context.getRequestId(), entry.getValue());
                arrangement.put(entry.getKey(), treatmentCode);
            }
            ExperimentContext experimentContext = new ExperimentContext(arrangement, experimentConfiguration);
            log.debug("experimentContext: {}", experimentContext);
            context.setExperimentContext(experimentContext);
        } catch (Exception e) {
            context.addError("Error while loading experiment configuration.\n" + e.getMessage());
            throw new IllegalStateException("Error while getting ExperimentContext", e);
        }
    }
}
