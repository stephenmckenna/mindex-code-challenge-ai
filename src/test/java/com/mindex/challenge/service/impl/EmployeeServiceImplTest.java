package com.mindex.challenge.service.impl;

import com.mindex.challenge.data.Employee;
import com.mindex.challenge.exception.EmployeeNotFoundException;
import com.mindex.challenge.service.EmployeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EmployeeServiceImplTest {

    private String employeeUrl;
    private String employeeIdUrl;

    @Autowired
    private EmployeeService employeeService;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    public void setup() {
        employeeUrl = "http://localhost:" + port + "/employee";
        employeeIdUrl = "http://localhost:" + port + "/employee/{id}";
    }

    @Test
    public void testCreateReadUpdate() {
        Employee testEmployee = new Employee();
        testEmployee.setFirstName("John");
        testEmployee.setLastName("Doe");
        testEmployee.setDepartment("Engineering");
        testEmployee.setPosition("Developer");

        // Create checks
        Employee createdEmployee = restTemplate.postForEntity(employeeUrl, testEmployee, Employee.class).getBody();

        assertNotNull(createdEmployee.getEmployeeId());
        assertEmployeeEquivalence(testEmployee, createdEmployee);


        // Read checks
        Employee readEmployee = restTemplate.getForEntity(employeeIdUrl, Employee.class, createdEmployee.getEmployeeId()).getBody();
        assertEquals(createdEmployee.getEmployeeId(), readEmployee.getEmployeeId());
        assertEmployeeEquivalence(createdEmployee, readEmployee);


        // Update checks
        readEmployee.setPosition("Development Manager");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Employee updatedEmployee =
                restTemplate.exchange(employeeIdUrl,
                        HttpMethod.PUT,
                        new HttpEntity<Employee>(readEmployee, headers),
                        Employee.class,
                        readEmployee.getEmployeeId()).getBody();

        assertEmployeeEquivalence(readEmployee, updatedEmployee);
    }

    @Test
    public void testCreateAssignsNewIdIgnoringClientId() {
        Employee testEmployee = Employee.builder()
                .employeeId("client-chosen-id")
                .firstName("Jane")
                .lastName("Doe")
                .build();

        Employee createdEmployee = employeeService.create(testEmployee);

        assertNotNull(createdEmployee.getEmployeeId());
        assertNotEquals("client-chosen-id", createdEmployee.getEmployeeId());
        assertEmployeeEquivalence(testEmployee, employeeService.read(createdEmployee.getEmployeeId()));
    }

    @Test
    public void testReadUnknownIdThrowsNotFound() {
        EmployeeNotFoundException exception =
                assertThrows(EmployeeNotFoundException.class, () -> employeeService.read("does-not-exist"));

        assertEquals("Invalid employeeId: does-not-exist", exception.getMessage());
    }

    @Test
    public void testUpdatePersistsChanges() {
        Employee createdEmployee = employeeService.create(Employee.builder()
                .firstName("Jane")
                .lastName("Doe")
                .position("Developer")
                .build());

        createdEmployee.setPosition("Development Manager");
        employeeService.update(createdEmployee);

        assertEquals("Development Manager", employeeService.read(createdEmployee.getEmployeeId()).getPosition());
    }

    private static void assertEmployeeEquivalence(Employee expected, Employee actual) {
        assertEquals(expected.getFirstName(), actual.getFirstName());
        assertEquals(expected.getLastName(), actual.getLastName());
        assertEquals(expected.getDepartment(), actual.getDepartment());
        assertEquals(expected.getPosition(), actual.getPosition());
    }
}
