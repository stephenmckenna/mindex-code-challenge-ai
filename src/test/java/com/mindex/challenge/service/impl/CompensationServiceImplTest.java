package com.mindex.challenge.service.impl;

import com.mindex.challenge.dao.CompensationRepository;
import com.mindex.challenge.data.Compensation;
import com.mindex.challenge.data.Employee;
import com.mindex.challenge.exception.CompensationConflictException;
import com.mindex.challenge.exception.EmployeeNotFoundException;
import com.mindex.challenge.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class CompensationServiceImplTest {

    private static final String EMPLOYEE_ID = "employee-1";
    private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2025, 1, 1);

    @Mock
    private CompensationRepository compensationRepository;

    @Mock
    private EmployeeService employeeService;

    @InjectMocks
    private CompensationServiceImpl compensationService;

    @Test
    void createAssignsIdsServerSideAndInserts() {
        givenEmployeeExists();
        givenInsertReturnsArgument();
        Compensation request = compensation(new BigDecimal("85000.00"), EFFECTIVE_DATE);
        request.setCompensationId("client-chosen-id");
        request.setEmployeeId("someone-else");

        Compensation created = compensationService.create(EMPLOYEE_ID, request);

        ArgumentCaptor<Compensation> inserted = ArgumentCaptor.forClass(Compensation.class);
        verify(compensationRepository).insert(inserted.capture());
        assertEquals(EMPLOYEE_ID, inserted.getValue().getEmployeeId());
        assertNotNull(inserted.getValue().getCompensationId());
        assertNotEquals("client-chosen-id", inserted.getValue().getCompensationId());
        assertEquals(new BigDecimal("85000.00"), created.getSalary());
        assertEquals(EFFECTIVE_DATE, created.getEffectiveDate());
    }

    @Test
    void createNeverUsesSaveSoExistingRecordsCannotBeOverwritten() {
        givenEmployeeExists();
        givenInsertReturnsArgument();

        compensationService.create(EMPLOYEE_ID, compensation(BigDecimal.TEN, EFFECTIVE_DATE));

        verify(compensationRepository, never()).save(any(Compensation.class));
    }

    @Test
    void eachCreateGetsADistinctId() {
        givenEmployeeExists();
        givenInsertReturnsArgument();

        Compensation first = compensationService.create(EMPLOYEE_ID, compensation(BigDecimal.TEN, EFFECTIVE_DATE));
        Compensation second = compensationService.create(EMPLOYEE_ID, compensation(BigDecimal.TEN, EFFECTIVE_DATE.plusYears(1)));

        assertNotEquals(first.getCompensationId(), second.getCompensationId());
    }

    @Test
    void createLogsCreatedCompensationAtInfo(CapturedOutput output) {
        givenEmployeeExists();
        givenInsertReturnsArgument();

        Compensation created = compensationService.create(EMPLOYEE_ID, compensation(BigDecimal.TEN, EFFECTIVE_DATE));

        assertTrue(output.getOut().contains("Created compensation [" + created.getCompensationId()
                + "] for employee [" + EMPLOYEE_ID + "] effective [2025-01-01]"));
    }

    @Test
    void createWithExistingEffectiveDateThrowsConflictNamingExistingRecord() {
        givenEmployeeExists();
        Compensation existing = compensation(new BigDecimal("70000"), EFFECTIVE_DATE);
        existing.setCompensationId("existing-id");
        existing.setEmployeeId(EMPLOYEE_ID);
        when(compensationRepository.findByEmployeeIdAndEffectiveDate(EMPLOYEE_ID, EFFECTIVE_DATE))
                .thenReturn(Optional.of(existing));

        CompensationConflictException exception = assertThrows(CompensationConflictException.class,
                () -> compensationService.create(EMPLOYEE_ID, compensation(BigDecimal.TEN, EFFECTIVE_DATE)));

        assertTrue(exception.getMessage().contains(EMPLOYEE_ID));
        assertTrue(exception.getMessage().contains("2025-01-01"));
        assertTrue(exception.getMessage().contains("existing-id"));
        verify(compensationRepository, never()).insert(any(Compensation.class));
    }

    @Test
    void createForUnknownEmployeeThrowsNotFoundWithoutTouchingRepository() {
        givenEmployeeDoesNotExist();

        assertThrows(EmployeeNotFoundException.class,
                () -> compensationService.create(EMPLOYEE_ID, compensation(BigDecimal.TEN, EFFECTIVE_DATE)));
        verifyNoInteractions(compensationRepository);
    }

    @Test
    void readReturnsHistoryFromRepository() {
        givenEmployeeExists();
        List<Compensation> history = List.of(
                compensation(new BigDecimal("90000"), EFFECTIVE_DATE.plusYears(1)),
                compensation(new BigDecimal("85000"), EFFECTIVE_DATE));
        when(compensationRepository.findByEmployeeIdOrderByEffectiveDateDesc(EMPLOYEE_ID)).thenReturn(history);

        assertEquals(history, compensationService.read(EMPLOYEE_ID));
    }

    @Test
    void readForEmployeeWithNoCompensationReturnsEmptyList() {
        givenEmployeeExists();
        when(compensationRepository.findByEmployeeIdOrderByEffectiveDateDesc(EMPLOYEE_ID)).thenReturn(List.of());

        assertTrue(compensationService.read(EMPLOYEE_ID).isEmpty());
    }

    @Test
    void readForUnknownEmployeeThrowsNotFoundWithoutTouchingRepository() {
        givenEmployeeDoesNotExist();

        assertThrows(EmployeeNotFoundException.class, () -> compensationService.read(EMPLOYEE_ID));
        verifyNoInteractions(compensationRepository);
    }

    private void givenEmployeeExists() {
        when(employeeService.read(EMPLOYEE_ID)).thenReturn(Employee.builder().employeeId(EMPLOYEE_ID).build());
    }

    private void givenEmployeeDoesNotExist() {
        when(employeeService.read(EMPLOYEE_ID)).thenThrow(new EmployeeNotFoundException(EMPLOYEE_ID));
    }

    private void givenInsertReturnsArgument() {
        when(compensationRepository.insert(any(Compensation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Compensation compensation(BigDecimal salary, LocalDate effectiveDate) {
        return Compensation.builder()
                .salary(salary)
                .effectiveDate(effectiveDate)
                .build();
    }
}
