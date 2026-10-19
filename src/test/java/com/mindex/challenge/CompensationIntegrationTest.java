package com.mindex.challenge;

import com.mindex.challenge.data.Compensation;
import com.mindex.challenge.data.Employee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
    End-to-end checks through HTTP against the real in-memory Mongo, covering what the mocked unit tests cannot:
    the derived queries (sorting by effectiveDate, matching on a LocalDate) and persistence of BigDecimal salaries.
    Every test creates its own employee so compensation records never leak between tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompensationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String employeeId;

    @BeforeEach
    void createEmployee() {
        Employee employee = Employee.builder().firstName("Jane").lastName("Doe").build();
        employeeId = restTemplate.postForEntity(baseUrl() + "/employee", employee, Employee.class)
                .getBody()
                .getEmployeeId();
    }

    @Test
    void createdCompensationIsPersistedAndReadBack() {
        ResponseEntity<Compensation> created = post(compensation("85000.00", "2025-01-01"));

        assertEquals(HttpStatus.OK, created.getStatusCode());
        assertNotNull(created.getBody().getCompensationId());
        assertEquals(employeeId, created.getBody().getEmployeeId());

        Compensation[] history = restTemplate.getForObject(compensationUrl(employeeId), Compensation[].class);
        assertEquals(1, history.length);
        assertEquals(created.getBody().getCompensationId(), history[0].getCompensationId());
        assertEquals(new BigDecimal("85000.00"), history[0].getSalary());
        assertEquals(LocalDate.of(2025, 1, 1), history[0].getEffectiveDate());
    }

    @Test
    void historyIsReturnedNewestFirstRegardlessOfInsertOrder() {
        post(compensation("85000", "2025-06-01"));
        post(compensation("70000", "2024-01-01"));
        post(compensation("95000", "2026-01-01"));

        Compensation[] history = restTemplate.getForObject(compensationUrl(employeeId), Compensation[].class);

        assertEquals(
                Arrays.asList(LocalDate.of(2026, 1, 1), LocalDate.of(2025, 6, 1), LocalDate.of(2024, 1, 1)),
                Arrays.stream(history).map(Compensation::getEffectiveDate).toList());
    }

    @Test
    void sameEffectiveDateReturnsConflictNamingExistingRecord() {
        Compensation existing = post(compensation("85000", "2025-01-01")).getBody();

        ResponseEntity<String> response = restTemplate.postForEntity(
                compensationUrl(employeeId), compensation("90000", "2025-01-01"), String.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().contains(existing.getCompensationId()));
        assertEquals(1, restTemplate.getForObject(compensationUrl(employeeId), Compensation[].class).length);
    }

    @Test
    void sameEffectiveDateForDifferentEmployeesIsAllowed() {
        post(compensation("85000", "2025-01-01"));
        String otherEmployeeId = restTemplate.postForEntity(baseUrl() + "/employee",
                Employee.builder().firstName("Other").build(), Employee.class).getBody().getEmployeeId();

        ResponseEntity<Compensation> response = restTemplate.postForEntity(
                compensationUrl(otherEmployeeId), compensation("85000", "2025-01-01"), Compensation.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void invalidBodyReturnsBadRequestNamingTheField() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                compensationUrl(employeeId), Map.of("effectiveDate", "2025-01-01"), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("\"field\":\"salary\""));
    }

    @Test
    void employeeWithNoCompensationReturnsEmptyList() {
        Compensation[] history = restTemplate.getForObject(compensationUrl(employeeId), Compensation[].class);

        assertEquals(0, history.length);
    }

    @Test
    void unknownEmployeeReturnsNotFound() {
        assertEquals(HttpStatus.NOT_FOUND,
                restTemplate.postForEntity(compensationUrl("does-not-exist"), compensation("85000", "2025-01-01"), String.class)
                        .getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                restTemplate.getForEntity(compensationUrl("does-not-exist"), String.class).getStatusCode());
    }

    @Test
    void salaryIsNotExposedThroughTheEmployeeEndpoint() {
        post(compensation("85000", "2025-01-01"));

        String employeeJson = restTemplate.getForObject(baseUrl() + "/employee/{id}", String.class, employeeId);

        assertFalse(employeeJson.contains("salary"));
    }

    private ResponseEntity<Compensation> post(Compensation compensation) {
        return restTemplate.postForEntity(compensationUrl(employeeId), compensation, Compensation.class);
    }

    private static Compensation compensation(String salary, String effectiveDate) {
        return Compensation.builder()
                .salary(new BigDecimal(salary))
                .effectiveDate(LocalDate.parse(effectiveDate))
                .build();
    }

    private String compensationUrl(String id) {
        return baseUrl() + "/employee/" + id + "/compensation";
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
