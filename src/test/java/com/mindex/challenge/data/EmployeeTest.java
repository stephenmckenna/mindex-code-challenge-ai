package com.mindex.challenge.data;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class EmployeeTest {

    @Autowired
    private JacksonTester<Employee> json;

    @Test
    void deserializesIdOnlyDirectReports() throws Exception {
        Employee employee = json.parseObject("""
                {
                  "employeeId": "manager",
                  "firstName": "John",
                  "directReports": [ { "employeeId": "report-1" }, { "employeeId": "report-2" } ]
                }
                """);

        assertThat(employee.getDirectReports())
                .extracting(Employee::getEmployeeId)
                .containsExactly("report-1", "report-2");
        assertThat(employee.getDirectReports().get(0).getFirstName()).isNull();
    }

    @Test
    void serializesWithoutNullFields() throws Exception {
        Employee employee = Employee.builder()
                .employeeId("manager")
                .firstName("John")
                .directReports(List.of(Employee.builder().employeeId("report-1").build()))
                .build();

        JsonContent<Employee> written = json.write(employee);

        assertThat(written).extractingJsonPathStringValue("$.directReports[0].employeeId").isEqualTo("report-1");
        assertThat(written).doesNotHaveJsonPath("$.directReports[0].firstName");
        assertThat(written).doesNotHaveJsonPath("$.lastName");
    }

    @Test
    void roundTripsToAnEqualEmployee() throws Exception {
        Employee employee = Employee.builder()
                .employeeId("manager")
                .firstName("John")
                .lastName("Lennon")
                .position("Development Manager")
                .department("Engineering")
                .directReports(List.of(Employee.builder().employeeId("report-1").build()))
                .build();

        assertThat(json.parseObject(json.write(employee).getJson())).isEqualTo(employee);
    }
}
