package com.mindex.challenge;

import com.mindex.challenge.data.Employee;
import com.mindex.challenge.data.ReportingStructure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
    End-to-end checks through HTTP against the real in-memory Mongo, covering what the mocked unit tests cannot:
    the derived findByEmployeeIdIn query and the seed data. Tests that need custom org charts create their own
    employees so they never alter the seed data other tests rely on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReportingStructureIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @ParameterizedTest(name = "{1} has {2} reports")
    @CsvSource({
            "16a596ae-edd3-4847-99fe-c4518e82c86f, John Lennon, 4",
            "03aa1462-ffa9-4978-901b-7c001562cf6f, Ringo Starr, 2",
            "b7839309-3348-463b-a7e3-5de1c168beb3, Paul McCartney, 0"
    })
    void seedDataReportCounts(String employeeId, String name, int expectedReports) {
        ResponseEntity<ReportingStructure> response = restTemplate.getForEntity(
                reportingStructureUrl(employeeId), ReportingStructure.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(employeeId, response.getBody().employee().getEmployeeId());
        assertEquals(name, response.getBody().employee().getFirstName() + " " + response.getBody().employee().getLastName());
        assertEquals(expectedReports, response.getBody().numberOfReports());
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void cycleCreatedThroughTheApiIsCountedOnce() {
        Employee a = createEmployee("A");
        Employee b = createEmployee("B");
        setDirectReports(a, b);
        setDirectReports(b, a);

        ResponseEntity<ReportingStructure> response = restTemplate.getForEntity(
                reportingStructureUrl(a.getEmployeeId()), ReportingStructure.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().numberOfReports());
    }

    @Test
    void unknownEmployeeReturnsNotFoundWithMessage() {
        ResponseEntity<String> response = restTemplate.getForEntity(reportingStructureUrl("does-not-exist"), String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid employeeId: does-not-exist"));
    }

    private Employee createEmployee(String firstName) {
        Employee employee = Employee.builder().firstName(firstName).lastName("Test").build();
        return restTemplate.postForEntity(baseUrl() + "/employee", employee, Employee.class).getBody();
    }

    private void setDirectReports(Employee manager, Employee... reports) {
        manager.setDirectReports(Arrays.stream(reports)
                .map(report -> Employee.builder().employeeId(report.getEmployeeId()).build())
                .toList());
        restTemplate.put(baseUrl() + "/employee/{id}", manager, manager.getEmployeeId());
    }

    private String reportingStructureUrl(String employeeId) {
        return baseUrl() + "/employee/" + employeeId + "/reportingStructure";
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
