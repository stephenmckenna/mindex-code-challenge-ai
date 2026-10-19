package com.mindex.challenge.controller;

import com.mindex.challenge.data.Employee;
import com.mindex.challenge.data.ReportingStructure;
import com.mindex.challenge.exception.EmployeeNotFoundException;
import com.mindex.challenge.service.ReportingStructureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportingStructureController.class)
class ReportingStructureControllerTest {

    private static final String EMPLOYEE_ID = "employee-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportingStructureService reportingStructureService;

    @Test
    void readReturnsEmployeeAndNumberOfReports() throws Exception {
        Employee employee = Employee.builder()
                .employeeId(EMPLOYEE_ID)
                .firstName("John")
                .directReports(List.of(Employee.builder().employeeId("report-1").build()))
                .build();
        when(reportingStructureService.read(EMPLOYEE_ID)).thenReturn(new ReportingStructure(employee, 4));

        mockMvc.perform(get("/employee/{id}/reportingStructure", EMPLOYEE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.employeeId").value(EMPLOYEE_ID))
                .andExpect(jsonPath("$.employee.firstName").value("John"))
                .andExpect(jsonPath("$.employee.directReports[0].employeeId").value("report-1"))
                .andExpect(jsonPath("$.numberOfReports").value(4));
    }

    @Test
    void readUnknownEmployeeReturnsNotFound() throws Exception {
        when(reportingStructureService.read("missing")).thenThrow(new EmployeeNotFoundException("missing"));

        MvcResult result = mockMvc.perform(get("/employee/{id}/reportingStructure", "missing"))
                .andExpect(status().isNotFound())
                .andReturn();

        assertInstanceOf(EmployeeNotFoundException.class, result.getResolvedException());
        assertEquals("Invalid employeeId: missing", result.getResolvedException().getMessage());
    }
}
