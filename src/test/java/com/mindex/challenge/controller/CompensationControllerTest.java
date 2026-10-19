package com.mindex.challenge.controller;

import com.mindex.challenge.data.Compensation;
import com.mindex.challenge.exception.CompensationConflictException;
import com.mindex.challenge.exception.EmployeeNotFoundException;
import com.mindex.challenge.service.CompensationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompensationController.class)
class CompensationControllerTest {

    private static final String EMPLOYEE_ID = "employee-1";
    private static final String VALID_BODY = """
            {"salary": 85000.00, "effectiveDate": "2025-01-01"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompensationService compensationService;

    @Test
    void createReturnsCreatedCompensation() throws Exception {
        when(compensationService.create(eq(EMPLOYEE_ID), any(Compensation.class)))
                .thenReturn(compensation("comp-1", "85000.00", LocalDate.of(2025, 1, 1)));

        mockMvc.perform(post("/employee/{id}/compensation", EMPLOYEE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compensationId").value("comp-1"))
                .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_ID))
                .andExpect(jsonPath("$.salary").value(85000.00))
                .andExpect(jsonPath("$.effectiveDate").value("2025-01-01"));
    }

    @Test
    void createPassesPathIdAndParsedBodyToService() throws Exception {
        when(compensationService.create(eq(EMPLOYEE_ID), any(Compensation.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        mockMvc.perform(post("/employee/{id}/compensation", EMPLOYEE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());

        verify(compensationService).create(eq(EMPLOYEE_ID), eq(Compensation.builder()
                .salary(new BigDecimal("85000.00"))
                .effectiveDate(LocalDate.of(2025, 1, 1))
                .build()));
    }

    @Test
    void createForUnknownEmployeeReturnsNotFound() throws Exception {
        when(compensationService.create(eq("missing"), any(Compensation.class)))
                .thenThrow(new EmployeeNotFoundException("missing"));

        MvcResult result = mockMvc.perform(post("/employee/{id}/compensation", "missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andReturn();

        assertInstanceOf(EmployeeNotFoundException.class, result.getResolvedException());
    }

    @Test
    void createWithConflictingEffectiveDateReturnsConflict() throws Exception {
        CompensationConflictException conflict =
                new CompensationConflictException(compensation("existing-id", "70000", LocalDate.of(2025, 1, 1)));
        when(compensationService.create(eq(EMPLOYEE_ID), any(Compensation.class))).thenThrow(conflict);

        MvcResult result = mockMvc.perform(post("/employee/{id}/compensation", EMPLOYEE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andReturn();

        assertEquals(conflict.getMessage(), result.getResolvedException().getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"effectiveDate\": \"2025-01-01\"}",                   // missing salary
            "{\"salary\": 0, \"effectiveDate\": \"2025-01-01\"}",    // zero salary
            "{\"salary\": -5, \"effectiveDate\": \"2025-01-01\"}",   // negative salary
            "{\"salary\": 85000}",                                   // missing effectiveDate
            "{\"salary\": 85000, \"effectiveDate\": \"01/01/2025\"}", // unparseable effectiveDate
            "{\"salary\": \"lots\", \"effectiveDate\": \"2025-01-01\"}" // non-numeric salary
    })
    void createWithInvalidBodyReturnsBadRequestWithoutCallingService(String body) throws Exception {
        mockMvc.perform(post("/employee/{id}/compensation", EMPLOYEE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(compensationService);
    }

    @Test
    void readReturnsHistoryInServiceOrder() throws Exception {
        when(compensationService.read(EMPLOYEE_ID)).thenReturn(List.of(
                compensation("comp-2", "90000", LocalDate.of(2026, 1, 1)),
                compensation("comp-1", "85000", LocalDate.of(2025, 1, 1))));

        mockMvc.perform(get("/employee/{id}/compensation", EMPLOYEE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].compensationId").value("comp-2"))
                .andExpect(jsonPath("$[0].effectiveDate").value("2026-01-01"))
                .andExpect(jsonPath("$[1].compensationId").value("comp-1"));
    }

    @Test
    void readWithNoCompensationReturnsEmptyList() throws Exception {
        when(compensationService.read(EMPLOYEE_ID)).thenReturn(List.of());

        mockMvc.perform(get("/employee/{id}/compensation", EMPLOYEE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void readForUnknownEmployeeReturnsNotFound() throws Exception {
        when(compensationService.read("missing")).thenThrow(new EmployeeNotFoundException("missing"));

        mockMvc.perform(get("/employee/{id}/compensation", "missing"))
                .andExpect(status().isNotFound());
    }

    private static Compensation compensation(String compensationId, String salary, LocalDate effectiveDate) {
        return Compensation.builder()
                .compensationId(compensationId)
                .employeeId(EMPLOYEE_ID)
                .salary(new BigDecimal(salary))
                .effectiveDate(effectiveDate)
                .build();
    }
}
