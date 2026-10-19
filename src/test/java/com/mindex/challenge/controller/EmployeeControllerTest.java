package com.mindex.challenge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindex.challenge.data.Employee;
import com.mindex.challenge.exception.EmployeeNotFoundException;
import com.mindex.challenge.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeeController.class)
class EmployeeControllerTest {

    private static final String EMPLOYEE_ID = "employee-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmployeeService employeeService;

    @Test
    void createReturnsCreatedEmployee() throws Exception {
        when(employeeService.create(any(Employee.class))).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.setEmployeeId(EMPLOYEE_ID);
            return employee;
        });

        mockMvc.perform(post("/employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(employee(null, "Developer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_ID))
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.position").value("Developer"));
    }

    @Test
    void readReturnsEmployee() throws Exception {
        when(employeeService.read(EMPLOYEE_ID)).thenReturn(employee(EMPLOYEE_ID, "Developer"));

        mockMvc.perform(get("/employee/{id}", EMPLOYEE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_ID))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    void readUnknownEmployeeReturnsNotFound() throws Exception {
        when(employeeService.read("missing")).thenThrow(new EmployeeNotFoundException("missing"));

        MvcResult result = mockMvc.perform(get("/employee/{id}", "missing"))
                .andExpect(status().isNotFound())
                .andReturn();

        assertInstanceOf(EmployeeNotFoundException.class, result.getResolvedException());
        assertEquals("Invalid employeeId: missing", result.getResolvedException().getMessage());
    }

    @Test
    void updateUsesIdFromPathOverIdInBody() throws Exception {
        when(employeeService.update(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/employee/{id}", EMPLOYEE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(employee("some-other-id", "Development Manager"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_ID))
                .andExpect(jsonPath("$.position").value("Development Manager"));

        ArgumentCaptor<Employee> updated = ArgumentCaptor.forClass(Employee.class);
        verify(employeeService).update(updated.capture());
        assertEquals(EMPLOYEE_ID, updated.getValue().getEmployeeId());
    }

    private static Employee employee(String employeeId, String position) {
        return Employee.builder()
                .employeeId(employeeId)
                .firstName("Jane")
                .lastName("Doe")
                .position(position)
                .department("Engineering")
                .build();
    }
}
