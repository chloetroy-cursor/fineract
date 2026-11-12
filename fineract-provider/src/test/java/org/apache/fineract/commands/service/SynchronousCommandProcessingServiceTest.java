/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.commands.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.apache.fineract.batch.exception.ErrorInfo;
import org.apache.fineract.commands.configuration.RetryConfigurationAssembler;
import org.apache.fineract.commands.domain.CommandProcessingResultType;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.exception.RollbackTransactionNotApprovedException;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.commands.provider.CommandHandlerProvider;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.domain.BatchRequestContextHolder;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractRequestContextHolder;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.exception.IdempotentCommandProcessSucceedException;
import org.apache.fineract.infrastructure.core.exception.IdempotentCommandProcessUnderProcessingException;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.TransactionBoundApplicationEventPublisher;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class SynchronousCommandProcessingServiceTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private TransactionBoundApplicationEventPublisher eventPublisher;
    @Mock
    private ToApiJsonSerializer<Map<String, Object>> toApiJsonSerializer;
    @Mock
    private ToApiJsonSerializer<CommandProcessingResult> toApiResultJsonSerializer;
    @Mock
    private ConfigurationDomainService configurationDomainService;
    @Mock
    private CommandHandlerProvider commandHandlerProvider;
    @Mock
    private IdempotencyKeyResolver idempotencyKeyResolver;
    @Mock
    private CommandSourceService commandSourceService;

    @Mock
    private RetryRegistry retryRegistry;

    @Mock
    private FineractProperties fineractProperties;

    @Mock
    private RetryConfigurationAssembler retryConfigurationAssembler;

    @Spy
    private FineractRequestContextHolder fineractRequestContextHolder;

    private final MockClock clock = new MockClock();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    private final ErrorHandler errorHandler = mock(ErrorHandler.class);

    // A real instance on a registry the tests can read. As a spy it is a candidate for the constructor of the
    // service under test; without it the service would be built with no metrics at all.
    @Spy
    private LoanCommandMetrics loanCommandMetrics = new LoanCommandMetrics(meterRegistry, errorHandler);

    @InjectMocks
    private SynchronousCommandProcessingService underTest;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        RequestContextHolder.resetRequestAttributes();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ErrorInfo errorInfo = mock(ErrorInfo.class);
        when(errorInfo.getMessage()).thenReturn("Failed");
        when(errorInfo.getStatusCode()).thenReturn(500);
        when(commandSourceService.generateErrorInfo(any())).thenReturn(errorInfo);

        FineractProperties.RetryProperties settings = new FineractProperties.RetryProperties();
        settings.setInstances(new FineractProperties.RetryProperties.InstancesProperties());
        settings.getInstances().setExecuteCommand(new FineractProperties.RetryProperties.InstancesProperties.ExecuteCommandProperties());
        settings.getInstances().getExecuteCommand().setMaxAttempts(3);
        settings.getInstances().getExecuteCommand().setWaitDuration(Duration.ofMillis(1));
        settings.getInstances().getExecuteCommand().setEnableExponentialBackoff(false);
        settings.getInstances().getExecuteCommand()
                .setRetryExceptions(new Class[] { RetryException.class, IdempotentCommandProcessUnderProcessingException.class });
        when(fineractProperties.getRetry()).thenReturn(settings);
        when(retryRegistry.retry(anyString(), any(RetryConfig.class)))
                .thenAnswer(i -> Retry.of((String) i.getArgument(0), (RetryConfig) i.getArgument(1)));

        var impl = new RetryConfigurationAssembler(retryRegistry, fineractProperties, fineractRequestContextHolder);
        var retry = impl.getRetryConfigurationForExecuteCommand();
        when(retryConfigurationAssembler.getRetryConfigurationForExecuteCommand()).thenReturn(retry);

        when(commandSourceService.processCommandAndSaveResult(any(NewCommandSourceHandler.class), any(JsonCommand.class),
                any(CommandSource.class), any(AppUser.class), Mockito.anyBoolean(),
                Mockito.<BiConsumer<CommandSource, CommandProcessingResult>>any())).thenAnswer(invocation -> {
                    NewCommandSourceHandler handler = invocation.getArgument(0);
                    JsonCommand command = invocation.getArgument(1);
                    CommandSource commandSource = invocation.getArgument(2);
                    BiConsumer<CommandSource, CommandProcessingResult> resultUpdater = invocation.getArgument(5);

                    CommandProcessingResult result = handler.processCommand(command);
                    resultUpdater.accept(commandSource, result);
                    CommandSource savedCommandSource = commandSourceService.saveResult(commandSource);
                    return new CommandSourceService.CommandExecutionResult(result, savedCommandSource);
                });
    }

    @AfterEach
    public void teardown() {
        reset(context);
        reset(applicationContext);
        reset(toApiJsonSerializer);
        reset(toApiResultJsonSerializer);
        reset(configurationDomainService);
        reset(commandHandlerProvider);
        reset(idempotencyKeyResolver);
        reset(commandSourceService);
        reset(retryConfigurationAssembler);
    }

    @Test
    public void testExecuteCommandSuccessAfter2Fails() {
        CommandWrapper commandWrapper = getCommandWrapper();

        long commandId = 1L;
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        when(jsonCommand.commandId()).thenReturn(commandId);

        NewCommandSourceHandler commandHandler = Mockito.mock(NewCommandSourceHandler.class);
        CommandProcessingResult commandProcessingResult = Mockito.mock(CommandProcessingResult.class);
        when(commandProcessingResult.isRollbackTransaction()).thenReturn(false);
        when(commandHandler.processCommand(jsonCommand)).thenThrow(new RetryException()).thenThrow(new RetryException())
                .thenReturn(commandProcessingResult);

        when(commandHandlerProvider.getHandler(Mockito.any(), Mockito.any())).thenReturn(commandHandler);

        when(configurationDomainService.isMakerCheckerEnabledForTask(Mockito.any())).thenReturn(false);
        String idk = "idk";
        when(idempotencyKeyResolver.resolve(commandWrapper)).thenReturn(idk);
        CommandSource commandSource = Mockito.mock(CommandSource.class);
        when(commandSource.getId()).thenReturn(commandId);
        when(commandSourceService.findCommandSource(commandWrapper, idk)).thenReturn(null);
        when(commandSourceService.getCommandSource(commandId)).thenReturn(commandSource);

        AppUser appUser = Mockito.mock(AppUser.class);
        when(commandSourceService.saveInitial(commandWrapper, jsonCommand, appUser, idk)).thenReturn(commandSource);
        when(commandSourceService.saveResult(commandSource)).thenReturn(commandSource);
        when(commandSource.getStatus()).thenReturn(CommandProcessingResultType.PROCESSED.getValue());
        when(context.authenticatedUser(Mockito.any(CommandWrapper.class))).thenReturn(appUser);

        CommandProcessingResult actualCommandProcessingResult = underTest.executeCommand(commandWrapper, jsonCommand, false);

        assertEquals(CommandProcessingResultType.PROCESSED.getValue(), commandSource.getStatus());
        assertEquals(commandProcessingResult, actualCommandProcessingResult);
        verify(commandSourceService, never()).generateErrorInfo(any());
        verify(commandSourceService).saveResult(commandSource);
    }

    /**
     * Test that an instance picked up an already under processing command. We assume that during retry timeouts it
     * stays in the same status therefor it should fail after reaching max retry count.
     */
    @Test
    public void executeCommandShouldFailAfterRetriesWithIdempotentCommandProcessUnderProcessingException() {
        CommandWrapper commandWrapper = Mockito.mock(CommandWrapper.class);
        when(commandWrapper.isDatatableResource()).thenReturn(false);
        when(commandWrapper.isNoteResource()).thenReturn(false);
        when(commandWrapper.isSurveyResource()).thenReturn(false);
        when(commandWrapper.isLoanDisburseDetailResource()).thenReturn(false);

        long commandId = 1L;
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        when(jsonCommand.commandId()).thenReturn(commandId);

        NewCommandSourceHandler commandHandler = Mockito.mock(NewCommandSourceHandler.class);
        CommandProcessingResult commandProcessingResult = Mockito.mock(CommandProcessingResult.class);
        when(commandProcessingResult.isRollbackTransaction()).thenReturn(false);
        when(commandHandler.processCommand(jsonCommand)).thenReturn(commandProcessingResult);

        when(commandHandlerProvider.getHandler(Mockito.any(), Mockito.any())).thenReturn(commandHandler);

        when(configurationDomainService.isMakerCheckerEnabledForTask(Mockito.any())).thenReturn(false);
        String idk = "idk";
        when(idempotencyKeyResolver.resolve(commandWrapper)).thenReturn(idk);
        CommandSource commandSource = Mockito.mock(CommandSource.class);
        when(commandSource.getId()).thenReturn(commandId);

        when(commandSourceService.findCommandSource(any(), any())).thenReturn(commandSource);

        when(commandSourceService.getCommandSource(commandId)).thenReturn(commandSource);

        AppUser appUser = Mockito.mock(AppUser.class);
        when(commandSourceService.saveInitial(commandWrapper, jsonCommand, appUser, idk)).thenReturn(commandSource);
        when(commandSourceService.saveResult(commandSource)).thenReturn(commandSource);
        when(commandSource.getStatus()).thenReturn(CommandProcessingResultType.UNDER_PROCESSING.getValue());
        when(context.authenticatedUser(Mockito.any(CommandWrapper.class))).thenReturn(appUser);

        when(retryConfigurationAssembler.getLastException()).thenReturn(null)
                .thenAnswer((i) -> IdempotentCommandProcessUnderProcessingException.class)
                .thenAnswer((i) -> IdempotentCommandProcessUnderProcessingException.class);

        assertThrows(IdempotentCommandProcessUnderProcessingException.class,
                () -> underTest.executeCommand(commandWrapper, jsonCommand, false));

        verify(commandSource, times(3)).getStatus();
        assertEquals(CommandProcessingResultType.UNDER_PROCESSING.getValue(), commandSource.getStatus());
        verify(commandSourceService, times(0)).generateErrorInfo(any());
        verify(commandSourceService, times(0)).saveResult(commandSource);
    }

    /**
     * Test that an instance picked up an already under processing command. We assume that during retry timeouts it is
     * moved out from retry and the process can pick it up. We expect 2 fails then the third time the command is
     * processable.
     */
    @Test
    public void executeCommandShouldPassAfter1retryFailsByIdempotentCommandProcessUnderProcessingException() {
        CommandWrapper commandWrapper = Mockito.mock(CommandWrapper.class);
        when(commandWrapper.isDatatableResource()).thenReturn(false);
        when(commandWrapper.isNoteResource()).thenReturn(false);
        when(commandWrapper.isSurveyResource()).thenReturn(false);
        when(commandWrapper.isLoanDisburseDetailResource()).thenReturn(false);

        long commandId = 1L;
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        when(jsonCommand.commandId()).thenReturn(commandId);

        NewCommandSourceHandler commandHandler = Mockito.mock(NewCommandSourceHandler.class);
        CommandProcessingResult commandProcessingResult = Mockito.mock(CommandProcessingResult.class);
        when(commandProcessingResult.isRollbackTransaction()).thenReturn(false);
        when(commandHandler.processCommand(jsonCommand)).thenReturn(commandProcessingResult);

        when(commandHandlerProvider.getHandler(Mockito.any(), Mockito.any())).thenReturn(commandHandler);

        when(configurationDomainService.isMakerCheckerEnabledForTask(Mockito.any())).thenReturn(false);
        String idk = "idk";
        when(idempotencyKeyResolver.resolve(commandWrapper)).thenReturn(idk);
        CommandSource commandSource = Mockito.mock(CommandSource.class);
        when(commandSource.getId()).thenReturn(commandId);

        when(commandSourceService.findCommandSource(any(), any())).thenReturn(commandSource);

        when(commandSourceService.getCommandSource(commandId)).thenReturn(commandSource);

        AppUser appUser = Mockito.mock(AppUser.class);
        when(commandSourceService.saveInitial(commandWrapper, jsonCommand, appUser, idk)).thenReturn(commandSource);
        when(commandSourceService.saveResult(commandSource)).thenReturn(commandSource);
        when(commandSource.getStatus()).thenReturn(CommandProcessingResultType.UNDER_PROCESSING.getValue()) //
                .thenReturn(CommandProcessingResultType.UNDER_PROCESSING.getValue()) //
                // Is it possible???
                .thenReturn(CommandProcessingResultType.AWAITING_APPROVAL.getValue()) //
        ;
        when(context.authenticatedUser(Mockito.any(CommandWrapper.class))).thenReturn(appUser);

        when(retryConfigurationAssembler.getLastException()).thenReturn(null)
                .thenAnswer((i) -> IdempotentCommandProcessUnderProcessingException.class);

        CommandProcessingResult actualCommandProcessingResult = underTest.executeCommand(commandWrapper, jsonCommand, false);

        verify(commandSource, times(3)).getStatus();
        verify(commandSourceService, times(0)).generateErrorInfo(any());
        verify(commandSourceService).saveResult(commandSource);
        assertEquals(actualCommandProcessingResult, commandProcessingResult);
    }

    /**
     * Test that an instance picked up a new command. During first processing, we expect a retryable exception to
     * happen, but commandSource should have already UNDER_PROCESSING status. We should try to reprocess. After 2nd time
     * fail, status should be still the same. On 3rd try it should result no error.
     */
    @Test
    public void executeCommandShouldPassAfter2RetriesOnRetryExceptionAndWithStuckStatus() {
        CommandWrapper commandWrapper = Mockito.mock(CommandWrapper.class);
        when(commandWrapper.isDatatableResource()).thenReturn(false);
        when(commandWrapper.isNoteResource()).thenReturn(false);
        when(commandWrapper.isSurveyResource()).thenReturn(false);
        when(commandWrapper.isLoanDisburseDetailResource()).thenReturn(false);

        long commandId = 1L;
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        when(jsonCommand.commandId()).thenReturn(commandId);

        NewCommandSourceHandler commandHandler = Mockito.mock(NewCommandSourceHandler.class);
        CommandProcessingResult commandProcessingResult = Mockito.mock(CommandProcessingResult.class);
        when(commandProcessingResult.isRollbackTransaction()).thenReturn(false);
        when(commandHandler.processCommand(jsonCommand))
                // first time
                .thenThrow(new RetryException())
                // look like stuck and fails
                .thenThrow(new RetryException())
                // look like stuck and pass
                .thenReturn(commandProcessingResult);

        when(commandHandlerProvider.getHandler(Mockito.any(), Mockito.any())).thenReturn(commandHandler);

        when(configurationDomainService.isMakerCheckerEnabledForTask(Mockito.any())).thenReturn(false);
        String idk = "idk";
        when(idempotencyKeyResolver.resolve(commandWrapper)).thenReturn(idk);
        CommandSource commandSource = Mockito.mock(CommandSource.class);
        when(commandSource.getId()).thenReturn(commandId);

        when(commandSourceService.getCommandSource(commandId)).thenReturn(commandSource);

        AppUser appUser = Mockito.mock(AppUser.class);
        when(commandSourceService.saveInitial(commandWrapper, jsonCommand, appUser, idk)).thenReturn(commandSource);
        when(commandSourceService.saveResult(commandSource)).thenReturn(commandSource);

        when(context.authenticatedUser(Mockito.any(CommandWrapper.class))).thenReturn(appUser);

        when(commandSourceService.findCommandSource(any(), any())).thenReturn(null) // simulate new Command
                .thenReturn(commandSource) // simulate stuck Command
                .thenReturn(commandSource); // simulate stuck Command

        when(commandSource.getStatus())
                // on first hit we don't have a command source because it is new.
                // on 2nd hit we have a stuck one
                .thenReturn(CommandProcessingResultType.UNDER_PROCESSING.getValue()) //
                .thenReturn(CommandProcessingResultType.UNDER_PROCESSING.getValue()); //

        when(retryConfigurationAssembler.getLastException()).thenAnswer((i) -> RetryException.class)
                .thenAnswer((i) -> RetryException.class);

        CommandProcessingResult actualCommandProcessingResult = underTest.executeCommand(commandWrapper, jsonCommand, false);

        verify(commandSource, times(2)).getStatus();
        assertEquals(CommandProcessingResultType.UNDER_PROCESSING.getValue(), commandSource.getStatus());
        verify(commandSourceService, never()).generateErrorInfo(any());
        verify(commandSourceService).saveResult(commandSource);
        assertEquals(actualCommandProcessingResult, commandProcessingResult);
    }

    @Test
    public void testExecuteCommandSuccess() {
        CommandWrapper commandWrapper = getCommandWrapper();

        long commandId = 1L;
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        when(jsonCommand.commandId()).thenReturn(commandId);

        NewCommandSourceHandler commandHandler = Mockito.mock(NewCommandSourceHandler.class);
        CommandProcessingResult commandProcessingResult = Mockito.mock(CommandProcessingResult.class);
        when(commandProcessingResult.isRollbackTransaction()).thenReturn(false);
        when(commandHandler.processCommand(jsonCommand)).thenReturn(commandProcessingResult);
        when(commandHandlerProvider.getHandler(Mockito.any(), Mockito.any())).thenReturn(commandHandler);

        when(configurationDomainService.isMakerCheckerEnabledForTask(Mockito.any())).thenReturn(false);
        String idk = "idk";
        when(idempotencyKeyResolver.resolve(commandWrapper)).thenReturn(idk);
        CommandSource commandSource = Mockito.mock(CommandSource.class);
        when(commandSource.getId()).thenReturn(commandId);
        when(commandSourceService.findCommandSource(commandWrapper, idk)).thenReturn(null);
        when(commandSourceService.getCommandSource(commandId)).thenReturn(commandSource);

        AppUser appUser = Mockito.mock(AppUser.class);
        when(commandSourceService.saveInitial(commandWrapper, jsonCommand, appUser, idk)).thenReturn(commandSource);
        when(commandSourceService.saveResult(commandSource)).thenReturn(commandSource);
        when(commandSource.getStatus()).thenReturn(CommandProcessingResultType.PROCESSED.getValue());
        when(context.authenticatedUser(Mockito.any(CommandWrapper.class))).thenReturn(appUser);

        CommandProcessingResult actualCommandProcessingResult = underTest.executeCommand(commandWrapper, jsonCommand, false);

        verify(commandSourceService).getCommandSource(commandId);
        assertEquals(CommandProcessingResultType.PROCESSED.getValue(), commandSource.getStatus());
        verify(commandSourceService).saveResult(commandSource);

        assertEquals(commandProcessingResult, actualCommandProcessingResult);
    }

    @Test
    public void testExecuteCommandFails() {
        CommandWrapper commandWrapper = getCommandWrapper();
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        Long commandId = jsonCommand.commandId();

        NewCommandSourceHandler commandHandler = Mockito.mock(NewCommandSourceHandler.class);
        CommandProcessingResult commandProcessingResult = Mockito.mock(CommandProcessingResult.class);
        CommandSource commandSource = Mockito.mock(CommandSource.class);
        when(commandSource.getId()).thenReturn(1L);
        when(commandProcessingResult.isRollbackTransaction()).thenReturn(false);
        RuntimeException runtimeException = new RuntimeException("foo");
        when(commandHandler.processCommand(jsonCommand)).thenThrow(runtimeException);
        when(commandHandlerProvider.getHandler(Mockito.any(), Mockito.any())).thenReturn(commandHandler);

        when(configurationDomainService.isMakerCheckerEnabledForTask(Mockito.any())).thenReturn(false);
        String idk = "idk";
        when(idempotencyKeyResolver.resolve(commandWrapper)).thenReturn(idk);
        when(commandSourceService.findCommandSource(commandWrapper, idk)).thenReturn(null);
        when(commandSourceService.getCommandSource(commandId)).thenReturn(commandSource);

        AppUser appUser = Mockito.mock(AppUser.class);
        when(appUser.getId()).thenReturn(1L);
        when(context.authenticatedUser(Mockito.any(CommandWrapper.class))).thenReturn(appUser);
        when(commandSourceService.saveInitial(commandWrapper, jsonCommand, appUser, idk)).thenReturn(commandSource);

        CommandSource initialCommandSource = Mockito.mock(CommandSource.class);

        when(commandSourceService.findCommandSource(commandWrapper, idk)).thenReturn(initialCommandSource);

        assertThrows(RuntimeException.class, () -> {
            underTest.executeCommand(commandWrapper, jsonCommand, false);
        });

        verify(commandSourceService).getCommandSource(commandId);
        verify(commandSourceService).generateErrorInfo(runtimeException);
    }

    @NonNull
    private static CommandWrapper getCommandWrapper() {
        CommandWrapper commandWrapper = Mockito.mock(CommandWrapper.class);
        when(commandWrapper.isDatatableResource()).thenReturn(false);
        when(commandWrapper.isNoteResource()).thenReturn(false);
        when(commandWrapper.isSurveyResource()).thenReturn(false);
        when(commandWrapper.isLoanDisburseDetailResource()).thenReturn(false);
        return commandWrapper;
    }

    @Test
    public void publishHookEventHandlesInvalidJson() {
        String entityName = "entity";
        String actionName = "action";
        JsonCommand command = Mockito.mock(JsonCommand.class);
        String invalidJson = "{ invalidJson }";

        when(command.json()).thenReturn(invalidJson);

        // Test that no exception is thrown (exceptions are caught and logged)
        assertDoesNotThrow(() -> {
            underTest.publishHookEvent(entityName, actionName, command, Object.class);
        });
    }

    private static final class RetryException extends RuntimeException {}

    @Test
    public void testExecuteCommandWithRetry() {
        CommandWrapper commandWrapper = getCommandWrapper();
        when(commandWrapper.isInterestPauseResource()).thenReturn(false);

        long commandId = 1L;
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        when(jsonCommand.commandId()).thenReturn(commandId);

        NewCommandSourceHandler commandHandler = Mockito.mock(NewCommandSourceHandler.class);
        CommandProcessingResult commandProcessingResult = Mockito.mock(CommandProcessingResult.class);
        when(commandProcessingResult.isRollbackTransaction()).thenReturn(false);
        when(commandHandler.processCommand(jsonCommand)).thenThrow(new RetryException()).thenReturn(commandProcessingResult);
        when(commandHandlerProvider.getHandler(Mockito.any(), Mockito.any())).thenReturn(commandHandler);

        when(configurationDomainService.isMakerCheckerEnabledForTask(Mockito.any())).thenReturn(false);
        String idempotencyKey = "test-idempotency-key";
        when(idempotencyKeyResolver.resolve(commandWrapper)).thenReturn(idempotencyKey);

        CommandSource commandSource = Mockito.mock(CommandSource.class);
        when(commandSource.getId()).thenReturn(commandId);
        when(commandSourceService.findCommandSource(commandWrapper, idempotencyKey)).thenReturn(null);

        AppUser appUser = Mockito.mock(AppUser.class);
        when(appUser.getId()).thenReturn(1L);
        when(context.authenticatedUser(Mockito.any(CommandWrapper.class))).thenReturn(appUser);

        when(commandSourceService.saveInitial(commandWrapper, jsonCommand, appUser, idempotencyKey)).thenReturn(commandSource);
        when(commandSourceService.saveResult(commandSource)).thenReturn(commandSource);

        // When fetching the command source after failure, return the same mock
        when(commandSourceService.getCommandSource(commandId)).thenReturn(commandSource);

        // Execute the command
        CommandProcessingResult result = underTest.executeCommand(commandWrapper, jsonCommand, false);

        verify(commandSource, atLeast(1)).setResultStatusCode(200);
        verify(commandSource, atLeast(1)).updateForAudit(commandProcessingResult);
        verify(commandSource, atLeast(1)).setResult(any());
        verify(commandSource, atLeast(1)).setStatus(CommandProcessingResultType.PROCESSED);
        verify(commandSourceService, times(2)).processCommandAndSaveResult(any(NewCommandSourceHandler.class), any(JsonCommand.class),
                any(CommandSource.class), any(AppUser.class), Mockito.anyBoolean(),
                Mockito.<BiConsumer<CommandSource, CommandProcessingResult>>any());
        verify(commandSourceService).saveResult(commandSource);
        verify(commandSourceService, never()).generateErrorInfo(any());

        assertEquals(commandProcessingResult, result);
    }

    @Test
    public void testExecuteCommandWithMaxRetryFailureStoresFinalErrorResult() {
        CommandWrapper commandWrapper = getCommandWrapper();
        when(commandWrapper.isInterestPauseResource()).thenReturn(false);

        long commandId = 1L;
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        when(jsonCommand.commandId()).thenReturn(commandId);

        NewCommandSourceHandler commandHandler = Mockito.mock(NewCommandSourceHandler.class);
        CommandProcessingResult commandProcessingResult = Mockito.mock(CommandProcessingResult.class);
        when(commandProcessingResult.isRollbackTransaction()).thenReturn(false);
        when(commandHandler.processCommand(jsonCommand)).thenReturn(commandProcessingResult);
        when(commandHandlerProvider.getHandler(Mockito.any(), Mockito.any())).thenReturn(commandHandler);

        when(configurationDomainService.isMakerCheckerEnabledForTask(Mockito.any())).thenReturn(false);
        String idempotencyKey = "test-idempotency-key";
        when(idempotencyKeyResolver.resolve(commandWrapper)).thenReturn(idempotencyKey);

        CommandSource commandSource = Mockito.mock(CommandSource.class);
        when(commandSource.getId()).thenReturn(commandId);
        when(commandSourceService.findCommandSource(commandWrapper, idempotencyKey)).thenReturn(null);

        AppUser appUser = Mockito.mock(AppUser.class);
        when(appUser.getId()).thenReturn(1L);
        when(context.authenticatedUser(Mockito.any(CommandWrapper.class))).thenReturn(appUser);

        when(commandSourceService.saveInitial(commandWrapper, jsonCommand, appUser, idempotencyKey)).thenReturn(commandSource);

        RetryException persistentException = new RetryException();
        when(commandHandler.processCommand(jsonCommand)).thenThrow(persistentException);

        when(commandSourceService.getCommandSource(commandId)).thenReturn(commandSource);

        RetryException exception = assertThrows(RetryException.class, () -> {
            underTest.executeCommand(commandWrapper, jsonCommand, false);
        });

        assertEquals(persistentException, exception);

        verify(commandSourceService, times(3)).processCommandAndSaveResult(any(NewCommandSourceHandler.class), any(JsonCommand.class),
                any(CommandSource.class), any(AppUser.class), Mockito.anyBoolean(),
                Mockito.<BiConsumer<CommandSource, CommandProcessingResult>>any());
        verify(commandSourceService, times(3)).getCommandSource(commandId);
        verify(commandSourceService).generateErrorInfo(persistentException);
        verify(commandSource).setResultStatusCode(500);
        verify(commandSource).setResult("Failed");
        verify(commandSource).setStatus(CommandProcessingResultType.ERROR);
        verify(commandSourceService).saveResult(commandSource);
    }

    @Test
    public void testMakerCheckerRollbackStoresAuditResultOutsideRolledBackCommandTransaction() {
        CommandWrapper commandWrapper = getCommandWrapper();
        when(commandWrapper.isInterestPauseResource()).thenReturn(false);

        long commandId = 1L;
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        when(jsonCommand.commandId()).thenReturn(commandId);

        NewCommandSourceHandler commandHandler = Mockito.mock(NewCommandSourceHandler.class);
        when(commandHandlerProvider.getHandler(Mockito.any(), Mockito.any())).thenReturn(commandHandler);

        String idempotencyKey = "test-idempotency-key";
        when(idempotencyKeyResolver.resolve(commandWrapper)).thenReturn(idempotencyKey);

        CommandSource commandSource = Mockito.mock(CommandSource.class);
        when(commandSource.getId()).thenReturn(commandId);
        when(commandSourceService.findCommandSource(commandWrapper, idempotencyKey)).thenReturn(null);

        AppUser appUser = Mockito.mock(AppUser.class);
        when(appUser.getId()).thenReturn(1L);
        when(context.authenticatedUser(Mockito.any(CommandWrapper.class))).thenReturn(appUser);

        when(commandSourceService.saveInitial(commandWrapper, jsonCommand, appUser, idempotencyKey)).thenReturn(commandSource);
        when(commandSourceService.getCommandSource(commandId)).thenReturn(commandSource);

        RollbackTransactionNotApprovedException rollbackException = new RollbackTransactionNotApprovedException(commandId, 99L);
        ErrorInfo makerCheckerErrorInfo = mock(ErrorInfo.class);
        when(makerCheckerErrorInfo.getMessage()).thenReturn("maker-checker-result");
        when(makerCheckerErrorInfo.getStatusCode()).thenReturn(200);
        when(commandSourceService.processCommandAndSaveResult(any(NewCommandSourceHandler.class), any(JsonCommand.class),
                any(CommandSource.class), any(AppUser.class), Mockito.anyBoolean(),
                Mockito.<BiConsumer<CommandSource, CommandProcessingResult>>any())).thenThrow(rollbackException);
        when(commandSourceService.generateErrorInfo(rollbackException)).thenReturn(makerCheckerErrorInfo);

        RollbackTransactionNotApprovedException actual = assertThrows(RollbackTransactionNotApprovedException.class,
                () -> underTest.executeCommand(commandWrapper, jsonCommand, false));

        assertEquals(rollbackException, actual);
        verify(commandSource).setResultStatusCode(200);
        verify(commandSource).setResult("maker-checker-result");
        verify(commandSourceService).saveResult(commandSource);
        verify(commandSourceService).generateErrorInfo(rollbackException);
    }

    /**
     * Test that when running inside an enclosing batch transaction, hook events are NOT published immediately but
     * deferred to afterCommit. This prevents webhooks (e.g. SMS notifications) from firing for commands that are
     * subsequently rolled back when a later command in the batch fails.
     */
    @Test
    public void testHookEventDeferredInEnclosingTransaction() {
        CommandWrapper commandWrapper = getCommandWrapper();

        long commandId = 1L;
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        when(jsonCommand.commandId()).thenReturn(commandId);

        NewCommandSourceHandler commandHandler = Mockito.mock(NewCommandSourceHandler.class);
        CommandProcessingResult commandProcessingResult = Mockito.mock(CommandProcessingResult.class);
        when(commandProcessingResult.isRollbackTransaction()).thenReturn(false);
        when(commandHandler.processCommand(jsonCommand)).thenReturn(commandProcessingResult);
        when(commandHandlerProvider.getHandler(Mockito.any(), Mockito.any())).thenReturn(commandHandler);

        when(configurationDomainService.isMakerCheckerEnabledForTask(Mockito.any())).thenReturn(false);
        String idk = "idk";
        when(idempotencyKeyResolver.resolve(commandWrapper)).thenReturn(idk);
        CommandSource commandSource = Mockito.mock(CommandSource.class);
        when(commandSource.getId()).thenReturn(commandId);
        when(commandSourceService.findCommandSource(commandWrapper, idk)).thenReturn(null);
        when(commandSourceService.getCommandSource(commandId)).thenReturn(commandSource);

        AppUser appUser = Mockito.mock(AppUser.class);
        when(context.authenticatedUser(Mockito.any(CommandWrapper.class))).thenReturn(appUser);
        when(commandSourceService.saveInitial(commandWrapper, jsonCommand, appUser, idk)).thenReturn(commandSource);
        when(commandSourceService.saveResult(commandSource)).thenReturn(commandSource);
        when(commandSource.getStatus()).thenReturn(CommandProcessingResultType.PROCESSED.getValue());

        // Simulate enclosing batch transaction with active synchronization
        BatchRequestContextHolder.setIsEnclosingTransaction(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            underTest.executeCommand(commandWrapper, jsonCommand, false);

            // The hook event should be deferred via TransactionSynchronization registered on the current transaction
            assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
            // Verify hook event was NOT published immediately (deferred to afterCommit)
            verify(eventPublisher, never()).publishEvent(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            BatchRequestContextHolder.resetIsEnclosingTransaction();
        }
    }

    /**
     * Test that when NOT in an enclosing transaction, no TransactionSynchronization is registered. The code takes the
     * immediate path (not the deferred path), preserving existing behaviour for non-batch single-request commands.
     */
    @Test
    public void testHookEventNotDeferredOutsideEnclosingTransaction() {
        CommandWrapper commandWrapper = getCommandWrapper();

        long commandId = 1L;
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        when(jsonCommand.commandId()).thenReturn(commandId);
        when(jsonCommand.json()).thenReturn(null); // null causes publishHookEvent to exit early; avoids mocking the
                                                   // full hook-serialisation chain

        NewCommandSourceHandler commandHandler = Mockito.mock(NewCommandSourceHandler.class);
        CommandProcessingResult commandProcessingResult = Mockito.mock(CommandProcessingResult.class);
        when(commandProcessingResult.isRollbackTransaction()).thenReturn(false);
        when(commandHandler.processCommand(jsonCommand)).thenReturn(commandProcessingResult);
        when(commandHandlerProvider.getHandler(Mockito.any(), Mockito.any())).thenReturn(commandHandler);

        when(configurationDomainService.isMakerCheckerEnabledForTask(Mockito.any())).thenReturn(false);
        String idk = "idk";
        when(idempotencyKeyResolver.resolve(commandWrapper)).thenReturn(idk);
        CommandSource commandSource = Mockito.mock(CommandSource.class);
        when(commandSource.getId()).thenReturn(commandId);
        when(commandSourceService.findCommandSource(commandWrapper, idk)).thenReturn(null);
        when(commandSourceService.getCommandSource(commandId)).thenReturn(commandSource);

        AppUser appUser = Mockito.mock(AppUser.class);
        when(context.authenticatedUser(Mockito.any(CommandWrapper.class))).thenReturn(appUser);
        when(commandSourceService.saveInitial(commandWrapper, jsonCommand, appUser, idk)).thenReturn(commandSource);
        when(commandSourceService.saveResult(commandSource)).thenReturn(commandSource);
        when(commandSource.getStatus()).thenReturn(CommandProcessingResultType.PROCESSED.getValue());

        // Not in enclosing transaction (default)
        BatchRequestContextHolder.resetIsEnclosingTransaction();

        underTest.executeCommand(commandWrapper, jsonCommand, false);

        // The deferred path was not taken: no TransactionSynchronization should have been registered.
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
    }

    @Test
    public void loanRepaymentThatSucceedsRecordsOneRepaySuccessSampleAndReturnsTheHandlerResult() {
        CommandWrapper repayment = new CommandWrapperBuilder().loanRepaymentTransaction(4711L).build();
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        CommandProcessingResult handlerResult = Mockito.mock(CommandProcessingResult.class);
        NewCommandSourceHandler commandHandler = stubCommandThatReachesItsHandler(jsonCommand);
        when(commandHandler.processCommand(jsonCommand)).thenAnswer(invocation -> {
            clock.add(120, TimeUnit.MILLISECONDS);
            return handlerResult;
        });

        CommandProcessingResult actual = underTest.executeCommand(repayment, jsonCommand, false);

        assertSame(handlerResult, actual);
        verify(commandHandlerProvider).getHandler("LOAN", "REPAYMENT");
        assertEquals(1, loanCommandTimer("repay", "success").count());
        assertEquals(120.0, loanCommandTimer("repay", "success").totalTime(TimeUnit.MILLISECONDS));
        assertEquals(1.0, loanCommandCounter("repay", "success"));
        assertOnlyRecordedLoanCommandSample("repay", "success");
        verify(errorHandler, never()).handle(any());
    }

    @Test
    public void loanCommandThatFailsWithA500RecordsServerErrorAndRethrowsTheSameException() {
        CommandWrapper disbursal = new CommandWrapperBuilder().disburseLoanApplication(4711L).build();
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        NewCommandSourceHandler commandHandler = stubCommandThatReachesItsHandler(jsonCommand);
        IllegalStateException thrownByHandler = new IllegalStateException("handler failed");
        when(commandHandler.processCommand(jsonCommand)).thenAnswer(invocation -> {
            clock.add(30, TimeUnit.MILLISECONDS);
            throw thrownByHandler;
        });

        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> underTest.executeCommand(disbursal, jsonCommand, false));

        assertSame(thrownByHandler, actual);
        assertEquals("handler failed", actual.getMessage());
        assertEquals(0, actual.getSuppressed().length);
        assertEquals(1, loanCommandTimer("disburse", "server_error").count());
        assertEquals(30.0, loanCommandTimer("disburse", "server_error").totalTime(TimeUnit.MILLISECONDS));
        assertOnlyRecordedLoanCommandSample("disburse", "server_error");
        // The status comes from the ErrorInfo the audit result was already built from, so the exception is mapped once.
        verify(commandSourceService, times(1)).generateErrorInfo(thrownByHandler);
        verify(errorHandler, never()).handle(any());
    }

    @Test
    public void loanCommandThatFailsWithA403RecordsClientErrorAndRethrowsTheSameException() {
        CommandWrapper approval = new CommandWrapperBuilder().approveLoanApplication(4711L).build();
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        NewCommandSourceHandler commandHandler = stubCommandThatReachesItsHandler(jsonCommand);
        IllegalArgumentException thrownByHandler = new IllegalArgumentException("domain rule violated");
        when(commandHandler.processCommand(jsonCommand)).thenThrow(thrownByHandler);
        ErrorInfo forbidden = mock(ErrorInfo.class);
        when(forbidden.getMessage()).thenReturn("Forbidden");
        when(forbidden.getStatusCode()).thenReturn(403);
        when(commandSourceService.generateErrorInfo(thrownByHandler)).thenReturn(forbidden);

        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class,
                () -> underTest.executeCommand(approval, jsonCommand, false));

        assertSame(thrownByHandler, actual);
        assertEquals(0, actual.getSuppressed().length);
        assertOnlyRecordedLoanCommandSample("approve", "client_error");
        verify(commandSourceService, times(1)).generateErrorInfo(thrownByHandler);
        verify(errorHandler, never()).handle(any());
    }

    @Test
    public void loanCommandThatFailsBeforeItsAuditRowExistsIsClassifiedByTheErrorHandler() {
        CommandWrapper submission = new CommandWrapperBuilder().createLoanApplication().build();
        JsonCommand jsonCommand = newCommandWithoutAuditRow();
        IllegalStateException thrownBeforeAudit = new IllegalStateException("no idempotency key");
        when(idempotencyKeyResolver.resolve(submission)).thenThrow(thrownBeforeAudit);
        when(errorHandler.handle(thrownBeforeAudit)).thenReturn(new ErrorInfo(400, 1001, "Bad request", null));

        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> underTest.executeCommand(submission, jsonCommand, false));

        assertSame(thrownBeforeAudit, actual);
        assertOnlyRecordedLoanCommandSample("submit", "client_error");
        verify(commandSourceService, never()).generateErrorInfo(any());
        verify(errorHandler, times(1)).handle(thrownBeforeAudit);
    }

    @Test
    public void loanCommandThatThrowsAnErrorRecordsServerErrorAndRethrowsTheSameError() {
        CommandWrapper repayment = new CommandWrapperBuilder().loanRepaymentTransaction(4711L).build();
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        NewCommandSourceHandler commandHandler = stubCommandThatReachesItsHandler(jsonCommand);
        StackOverflowError thrownByHandler = new StackOverflowError("deep");
        when(commandHandler.processCommand(jsonCommand)).thenThrow(thrownByHandler);

        StackOverflowError actual = assertThrows(StackOverflowError.class, () -> underTest.executeCommand(repayment, jsonCommand, false));

        assertSame(thrownByHandler, actual);
        assertOnlyRecordedLoanCommandSample("repay", "server_error");
        verify(errorHandler, never()).handle(any());
    }

    @Test
    public void threeAttemptsUnderRetryRecordOneSampleThatCoversAllOfThem() {
        CommandWrapper repayment = new CommandWrapperBuilder().loanRepaymentTransaction(4711L).build();
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        CommandProcessingResult handlerResult = Mockito.mock(CommandProcessingResult.class);
        NewCommandSourceHandler commandHandler = stubCommandThatReachesItsHandler(jsonCommand);
        when(commandHandler.processCommand(jsonCommand)).thenAnswer(invocation -> {
            clock.add(40, TimeUnit.MILLISECONDS);
            throw new RetryException();
        }).thenAnswer(invocation -> {
            clock.add(40, TimeUnit.MILLISECONDS);
            throw new RetryException();
        }).thenAnswer(invocation -> {
            clock.add(40, TimeUnit.MILLISECONDS);
            return handlerResult;
        });

        CommandProcessingResult actual = underTest.executeCommand(repayment, jsonCommand, false);

        assertSame(handlerResult, actual);
        verify(commandHandler, times(3)).processCommand(jsonCommand);
        assertEquals(1, loanCommandTimer("repay", "success").count());
        assertEquals(120.0, loanCommandTimer("repay", "success").totalTime(TimeUnit.MILLISECONDS));
        assertOnlyRecordedLoanCommandSample("repay", "success");
    }

    @Test
    public void retriesThatRunOutRecordOneFailureSample() {
        CommandWrapper repayment = new CommandWrapperBuilder().loanRepaymentTransaction(4711L).build();
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        NewCommandSourceHandler commandHandler = stubCommandThatReachesItsHandler(jsonCommand);
        RetryException persistent = new RetryException();
        when(commandHandler.processCommand(jsonCommand)).thenThrow(persistent);

        RetryException actual = assertThrows(RetryException.class, () -> underTest.executeCommand(repayment, jsonCommand, false));

        assertSame(persistent, actual);
        verify(commandHandler, times(3)).processCommand(jsonCommand);
        assertOnlyRecordedLoanCommandSample("repay", "server_error");
    }

    @Test
    public void makerCheckerRollbackOfALoanCommandRecordsAwaitingApproval() {
        CommandWrapper approval = new CommandWrapperBuilder().approveLoanApplication(4711L).build();
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        stubCommandThatReachesItsHandler(jsonCommand);
        RollbackTransactionNotApprovedException rollback = new RollbackTransactionNotApprovedException(1L, 4711L);
        ErrorInfo makerCheckerErrorInfo = mock(ErrorInfo.class);
        when(makerCheckerErrorInfo.getMessage()).thenReturn("maker-checker-result");
        when(makerCheckerErrorInfo.getStatusCode()).thenReturn(200);
        when(commandSourceService.processCommandAndSaveResult(any(NewCommandSourceHandler.class), any(JsonCommand.class),
                any(CommandSource.class), any(AppUser.class), Mockito.anyBoolean(),
                Mockito.<BiConsumer<CommandSource, CommandProcessingResult>>any())).thenThrow(rollback);
        when(commandSourceService.generateErrorInfo(rollback)).thenReturn(makerCheckerErrorInfo);

        RollbackTransactionNotApprovedException actual = assertThrows(RollbackTransactionNotApprovedException.class,
                () -> underTest.executeCommand(approval, jsonCommand, false));

        assertSame(rollback, actual);
        assertOnlyRecordedLoanCommandSample("approve", "awaiting_approval");
        verify(errorHandler, never()).handle(any());
    }

    @Test
    public void idempotentReplayOfALoanCommandRecordsDuplicateAndDoesNotRunTheHandler() {
        CommandWrapper repayment = new CommandWrapperBuilder().loanRepaymentTransaction(4711L).build();
        JsonCommand jsonCommand = newCommandWithoutAuditRow();
        String idempotencyKey = "already-seen";
        when(idempotencyKeyResolver.resolve(repayment)).thenReturn(idempotencyKey);
        CommandSource processedEarlier = Mockito.mock(CommandSource.class);
        when(processedEarlier.getStatus()).thenReturn(CommandProcessingResultType.PROCESSED.getValue());
        when(processedEarlier.getResult()).thenReturn("{\"resourceId\":99}");
        when(processedEarlier.getResultStatusCode()).thenReturn(200);
        when(commandSourceService.findCommandSource(repayment, idempotencyKey)).thenReturn(processedEarlier);

        IdempotentCommandProcessSucceedException actual = assertThrows(IdempotentCommandProcessSucceedException.class,
                () -> underTest.executeCommand(repayment, jsonCommand, false));

        assertEquals("{\"resourceId\":99}", actual.getResponse());
        assertOnlyRecordedLoanCommandSample("repay", "duplicate");
        verify(commandHandlerProvider, never()).getHandler(any(), any());
        verify(errorHandler, never()).handle(any());
    }

    @Test
    public void commandThatIsNotALoanCommandRecordsNothing() {
        CommandWrapper clientCreation = new CommandWrapperBuilder().createClient().build();
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        CommandProcessingResult handlerResult = Mockito.mock(CommandProcessingResult.class);
        NewCommandSourceHandler commandHandler = stubCommandThatReachesItsHandler(jsonCommand);
        when(commandHandler.processCommand(jsonCommand)).thenReturn(handlerResult);

        CommandProcessingResult actual = underTest.executeCommand(clientCreation, jsonCommand, false);

        assertSame(handlerResult, actual);
        verify(commandHandlerProvider).getHandler("CLIENT", "CREATE");
        assertNoLoanCommandSample();
        verify(loanCommandMetrics, never()).start();
    }

    @Test
    public void loanCommandOutsideTheRecordedSetRecordsNothingEvenWhenItFails() {
        CommandWrapper interestWaiver = new CommandWrapperBuilder().waiveInterestPortionTransaction(4711L).build();
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        NewCommandSourceHandler commandHandler = stubCommandThatReachesItsHandler(jsonCommand);
        IllegalStateException thrownByHandler = new IllegalStateException("handler failed");
        when(commandHandler.processCommand(jsonCommand)).thenThrow(thrownByHandler);

        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> underTest.executeCommand(interestWaiver, jsonCommand, false));

        assertSame(thrownByHandler, actual);
        assertNoLoanCommandSample();
    }

    /**
     * The cardinality guard: whatever identifies the loan, the client, the request or the tenant stays out of the
     * meters, so two repayments on different loans of different clients are two samples of one series.
     */
    @Test
    public void repaymentsOnDifferentLoansClientsAndTenantsLandInOneSeriesWithoutIdentifyingTags() {
        List<String> identifiers = List.of("4711", "9302", "8801", "8802", "acme-bank", "globex-bank", "loans", "idempotency-key");
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        CommandProcessingResult handlerResult = Mockito.mock(CommandProcessingResult.class);
        when(handlerResult.getLoanId()).thenReturn(4711L);
        when(handlerResult.getClientId()).thenReturn(8801L);
        NewCommandSourceHandler commandHandler = stubCommandThatReachesItsHandler(jsonCommand);
        when(commandHandler.processCommand(jsonCommand)).thenReturn(handlerResult);
        CommandWrapper repaymentOnLoan4711 = new CommandWrapperBuilder().loanRepaymentTransaction(4711L).withClientId(8801L)
                .build("idempotency-key-1");
        CommandWrapper repaymentOnLoan9302 = new CommandWrapperBuilder().loanRepaymentTransaction(9302L).withClientId(8802L)
                .build("idempotency-key-2");

        try {
            ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "acme-bank", "Acme Bank", "UTC", null));
            underTest.executeCommand(repaymentOnLoan4711, jsonCommand, false);
            ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(2L, "globex-bank", "Globex Bank", "UTC", null));
            underTest.executeCommand(repaymentOnLoan9302, jsonCommand, false);
        } finally {
            ThreadLocalContextUtil.reset();
        }

        assertEquals(1, meterRegistry.find(LOAN_COMMAND_TIMER).timers().size(), "two loans must not create two timer series");
        assertEquals(2, loanCommandTimer("repay", "success").count());
        assertEquals(2.0, loanCommandCounter("repay", "success"));
        assertEquals(21, meterRegistry.getMeters().size(), "20 counters registered at startup and the one timer");
        for (Meter meter : meterRegistry.getMeters()) {
            assertEquals(Set.of("command", "outcome"), meter.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet()),
                    "tag keys of " + meter.getId());
            for (String identifier : identifiers) {
                assertFalse(meter.getId().getName().contains(identifier), meter.getId() + " carries " + identifier);
                assertTrue(meter.getId().getTags().stream().noneMatch(tag -> tag.getValue().contains(identifier)),
                        meter.getId() + " carries " + identifier);
            }
        }
    }

    private static final String LOAN_COMMAND_TIMER = "fineract.loan.command.execution";
    private static final String LOAN_COMMAND_COUNTER = "fineract.loan.commands";

    /**
     * Stubs everything a command needs to get from {@code executeCommand} to its handler and back, and returns that
     * handler for the test to script.
     */
    private NewCommandSourceHandler stubCommandThatReachesItsHandler(JsonCommand jsonCommand) {
        long commandId = 1L;
        when(jsonCommand.commandId()).thenReturn(commandId);
        CommandSource commandSource = Mockito.mock(CommandSource.class);
        when(commandSource.getId()).thenReturn(commandId);
        when(commandSourceService.getCommandSource(commandId)).thenReturn(commandSource);
        when(commandSourceService.saveResult(commandSource)).thenReturn(commandSource);
        AppUser appUser = Mockito.mock(AppUser.class);
        when(context.authenticatedUser(Mockito.any(CommandWrapper.class))).thenReturn(appUser);
        NewCommandSourceHandler commandHandler = Mockito.mock(NewCommandSourceHandler.class);
        when(commandHandlerProvider.getHandler(Mockito.any(), Mockito.any())).thenReturn(commandHandler);
        return commandHandler;
    }

    private static JsonCommand newCommandWithoutAuditRow() {
        JsonCommand jsonCommand = Mockito.mock(JsonCommand.class);
        // a mocked Long getter answers 0, which the service would take for the id of an existing audit row
        when(jsonCommand.commandId()).thenReturn(null);
        return jsonCommand;
    }

    private Timer loanCommandTimer(String command, String outcome) {
        Timer timer = meterRegistry.find(LOAN_COMMAND_TIMER).tag("command", command).tag("outcome", outcome).timer();
        assertNotNull(timer, "no " + LOAN_COMMAND_TIMER + " timer for " + command + "/" + outcome);
        return timer;
    }

    private double loanCommandCounter(String command, String outcome) {
        return meterRegistry.get(LOAN_COMMAND_COUNTER).tag("command", command).tag("outcome", outcome).counter().count();
    }

    private double sumOfLoanCommandCounters() {
        return meterRegistry.find(LOAN_COMMAND_COUNTER).counters().stream().mapToDouble(Counter::count).sum();
    }

    private void assertOnlyRecordedLoanCommandSample(String command, String outcome) {
        assertEquals(1, meterRegistry.find(LOAN_COMMAND_TIMER).timers().size(), "exactly one timer series must exist");
        assertEquals(1, loanCommandTimer(command, outcome).count());
        assertEquals(1.0, loanCommandCounter(command, outcome));
        assertEquals(1.0, sumOfLoanCommandCounters(), "no other counter may move");
    }

    private void assertNoLoanCommandSample() {
        assertEquals(20, meterRegistry.getMeters().size(), "only the counters registered at startup may exist");
        assertEquals(20, meterRegistry.find(LOAN_COMMAND_COUNTER).counters().size());
        assertEquals(0.0, sumOfLoanCommandCounters());
        assertTrue(meterRegistry.find(LOAN_COMMAND_TIMER).meters().isEmpty());
    }
}
