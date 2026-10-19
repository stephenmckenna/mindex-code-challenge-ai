package com.mindex.challenge.data;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class ReportingStructureTest {

    @Autowired
    private JacksonTester<ReportingStructure> json;

    @Test
    void serializesEmployeeAndNumberOfReports() throws Exception {
        ReportingStructure reportingStructure = new ReportingStructure(
                Employee.builder().employeeId("manager").firstName("John").build(), 4);

        JsonContent<ReportingStructure> written = json.write(reportingStructure);

        assertThat(written).extractingJsonPathStringValue("$.employee.employeeId").isEqualTo("manager");
        assertThat(written).extractingJsonPathStringValue("$.employee.firstName").isEqualTo("John");
        assertThat(written).extractingJsonPathNumberValue("$.numberOfReports").isEqualTo(4);
    }

    @Test
    void roundTripsToAnEqualRecord() throws Exception {
        ReportingStructure reportingStructure = new ReportingStructure(
                Employee.builder().employeeId("manager").firstName("John").build(), 4);

        assertThat(json.parseObject(json.write(reportingStructure).getJson())).isEqualTo(reportingStructure);
    }
}
