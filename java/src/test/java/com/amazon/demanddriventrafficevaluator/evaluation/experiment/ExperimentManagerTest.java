// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.experiment;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.EvaluationContext;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentDefinition;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ConfigurationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperimentManagerTest {

    @Mock
    private ConfigurationProvider<ExperimentConfiguration> mockProvider;

    @Mock
    private TreatmentAllocator mockAllocator;

    @Mock
    private ExperimentHandler mockHandler;

    @Mock
    private ExperimentConfiguration mockConfiguration;

    private ExperimentManager experimentManager;

    @BeforeEach
    void setUp() {
        experimentManager = new ExperimentManager(mockProvider, mockAllocator, mockHandler);
    }

    @Test
    void testSetupExperimentContextSuccess() throws Exception {
        // Arrange
        EvaluationContext context = new EvaluationContext();
        context.setRequestId("testRequestId");

        Map<String, ExperimentDefinition> experimentDefinitions = new HashMap<>();
        experimentDefinitions.put("exp1", ExperimentDefinition.builder().name("exp1").build());
        experimentDefinitions.put("exp2", ExperimentDefinition.builder().name("exp2").build());

        when(mockProvider.provide()).thenReturn(mockConfiguration);
        when(mockConfiguration.getExperimentDefinitionByName()).thenReturn(experimentDefinitions);
        when(mockAllocator.getTreatmentCode(anyString(), any(ExperimentDefinition.class)))
                .thenReturn("T", "C");
        when(mockConfiguration.getModelToExperiment()).thenReturn(Map.of());

        // Act
        experimentManager.setupExperimentContext(context);

        // Assert
        verify(mockProvider).provide();
        verify(mockAllocator, times(2)).getTreatmentCode(eq("testRequestId"), any(ExperimentDefinition.class));
        verifyNoInteractions(mockHandler);

        assertNotNull(context.getExperimentContext());
        assertEquals(
                Set.of("T", "C"),
                Set.of(
                        context.getExperimentContext().getTreatmentCode("exp1"),
                        context.getExperimentContext().getTreatmentCode("exp2")
                )
        );
        assertFalse(context.getDebugInfo().contains("[Error]"));
    }

    @Test
    void testSetupExperimentContextProviderException() throws Exception {
        // Arrange
        EvaluationContext context = new EvaluationContext();
        when(mockProvider.provide()).thenThrow(new RuntimeException("Configuration error"));

        // Act & Assert
        Exception exception = assertThrows(IllegalStateException.class, () ->
                experimentManager.setupExperimentContext(context));

        assertEquals("Error while getting ExperimentContext", exception.getMessage());
        assertEquals("""
                        [Error] Error while loading experiment configuration.
                        Configuration error
                        """,
                context.getDebugInfo().get(0));
        verify(mockProvider).provide();
        verifyNoInteractions(mockAllocator, mockHandler);
    }

    @Test
    void testSetupExperimentContextAllocatorException() throws Exception {
        // Arrange
        EvaluationContext context = new EvaluationContext();
        context.setRequestId("testRequestId");

        Map<String, ExperimentDefinition> experimentDefinitions = new HashMap<>();
        experimentDefinitions.put("exp1", ExperimentDefinition.builder().name("exp1").build());

        when(mockProvider.provide()).thenReturn(mockConfiguration);
        when(mockConfiguration.getExperimentDefinitionByName()).thenReturn(experimentDefinitions);
        when(mockAllocator.getTreatmentCode(anyString(), any(ExperimentDefinition.class)))
                .thenThrow(new RuntimeException("Allocation error"));

        // Act & Assert
        Exception exception = assertThrows(IllegalStateException.class, () ->
                experimentManager.setupExperimentContext(context));

        assertEquals("Error while getting ExperimentContext", exception.getMessage());
        assertEquals("""
                [Error] Error while loading experiment configuration.
                Allocation error
                """, context.getDebugInfo().get(0));
        verify(mockProvider).provide();
        verify(mockAllocator).getTreatmentCode(eq("testRequestId"), any(ExperimentDefinition.class));
        verifyNoInteractions(mockHandler);
    }

    @Test
    void testEarlierRequestKeepsItsTreatmentAfterAnotherSetup() {
        ExperimentManager manager = new ExperimentManager(mockProvider, mockAllocator, new ExperimentHandler());
        ExperimentDefinition definition = ExperimentDefinition.builder().name("pricing").build();
        ExperimentConfiguration configuration = new ExperimentConfiguration();
        configuration.setExperimentDefinitionByName(Map.of("pricing", definition));
        configuration.setModelToExperiment(Map.of());
        when(mockProvider.provide()).thenReturn(configuration);
        when(mockAllocator.getTreatmentCode(anyString(), any(ExperimentDefinition.class)))
                .thenAnswer(invocation -> "request-a".equals(invocation.getArgument(0)) ? "control" : "treatment");

        EvaluationContext first = new EvaluationContext();
        first.setRequestId("request-a");
        manager.setupExperimentContext(first);

        EvaluationContext second = new EvaluationContext();
        second.setRequestId("request-b");
        manager.setupExperimentContext(second);

        assertEquals("control", first.getExperimentContext().getTreatmentCode("pricing"));
        assertEquals("treatment", second.getExperimentContext().getTreatmentCode("pricing"));
    }
}
