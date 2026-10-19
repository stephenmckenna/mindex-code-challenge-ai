package com.mindex.challenge.service.impl;

import com.mindex.challenge.dao.EmployeeRepository;
import com.mindex.challenge.data.Employee;
import com.mindex.challenge.data.ReportingStructure;
import com.mindex.challenge.exception.EmployeeNotFoundException;
import com.mindex.challenge.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// Timeout on a separate thread so a cycle-handling regression fails fast instead of looping forever and hanging the build
@Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ReportingStructureServiceImplTest {

    @Mock
    private EmployeeService employeeService;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private ReportingStructureServiceImpl reportingStructureService;

    @Captor
    private ArgumentCaptor<Collection<String>> idsCaptor;

    /*
        Same shape as the seed data:
                       john
                     /      \
                 paul        ringo
                            /     \
                         pete    george
     */
    private void givenSeedShapedTree() {
        givenEmployees(
                employee("john", "paul", "ringo"),
                employee("paul"),
                employee("ringo", "pete", "george"),
                employee("pete"),
                employee("george"));
    }

    @ParameterizedTest(name = "{0} has {1} reports")
    @CsvSource({"john, 4", "ringo, 2", "paul, 0", "pete, 0"})
    void countsAllDistinctReportsBeneathEmployee(String employeeId, int expectedReports) {
        givenSeedShapedTree();

        ReportingStructure result = reportingStructureService.read(employeeId);

        assertEquals(expectedReports, result.numberOfReports());
    }

    @Test
    void returnsEmployeeAsStored() {
        givenSeedShapedTree();
        Employee john = employeeService.read("john");

        ReportingStructure result = reportingStructureService.read("john");

        assertSame(john, result.employee());
    }

    @Test
    void nullDirectReportsCountsAsZeroWithoutQueryingRepository() {
        givenEmployees(employee("solo"));

        ReportingStructure result = reportingStructureService.read("solo");

        assertEquals(0, result.numberOfReports());
        verify(employeeRepository, never()).findByEmployeeIdIn(anyCollection());
    }

    @Test
    void emptyDirectReportsCountsAsZero() {
        Employee solo = employee("solo");
        solo.setDirectReports(List.of());
        givenEmployees(solo);

        assertEquals(0, reportingStructureService.read("solo").numberOfReports());
    }

    @Test
    void fetchesOneBatchPerLevelRatherThanOnePerEmployee() {
        givenSeedShapedTree();

        reportingStructureService.read("john");

        verify(employeeRepository, times(2)).findByEmployeeIdIn(idsCaptor.capture());
        List<Collection<String>> batches = idsCaptor.getAllValues();
        assertEquals(List.of("paul", "ringo"), new ArrayList<>(batches.get(0)));
        assertEquals(List.of("pete", "george"), new ArrayList<>(batches.get(1)));
    }

    @Test
    void cycleIsCountedOnceAndTerminates(CapturedOutput output) {
        givenEmployees(
                employee("a", "b"),
                employee("b", "a"));

        ReportingStructure result = reportingStructureService.read("a");

        assertEquals(1, result.numberOfReports());
        assertTrue(output.getOut().contains("reached more than once"));
    }

    @Test
    void longerCycleIsCountedOnceAndTerminates() {
        givenEmployees(
                employee("a", "b"),
                employee("b", "c"),
                employee("c", "a"));

        assertEquals(2, reportingStructureService.read("a").numberOfReports());
    }

    @Test
    void selfReferenceIsNotCounted() {
        givenEmployees(employee("a", "a"));

        assertEquals(0, reportingStructureService.read("a").numberOfReports());
    }

    @Test
    void employeeReachableThroughTwoManagersIsCountedOnce(CapturedOutput output) {
        givenEmployees(
                employee("a", "b", "c"),
                employee("b", "d"),
                employee("c", "d"),
                employee("d"));

        ReportingStructure result = reportingStructureService.read("a");

        assertEquals(3, result.numberOfReports());
        assertTrue(output.getOut().contains("Employee [d] reached more than once under [a]"));
    }

    @Test
    void danglingReportIdIsSkippedWithWarning(CapturedOutput output) {
        givenEmployees(employee("a", "b", "ghost"), employee("b"));

        ReportingStructure result = reportingStructureService.read("a");

        assertEquals(1, result.numberOfReports());
        assertTrue(output.getOut().contains("Direct report [ghost] under [a] does not exist"));
    }

    @Test
    void directReportWithoutIdIsIgnored() {
        Employee a = employee("a", "b");
        a.getDirectReports().add(new Employee());
        givenEmployees(a, employee("b"));

        assertEquals(1, reportingStructureService.read("a").numberOfReports());
    }

    @Test
    void unknownEmployeePropagatesNotFound() {
        givenEmployees();

        assertThrows(EmployeeNotFoundException.class, () -> reportingStructureService.read("missing"));
        verifyNoInteractions(employeeRepository);
    }

    // Backs both mocks with an in-memory directory so each test only declares the org chart it needs
    private void givenEmployees(Employee... employees) {
        Map<String, Employee> byId = Arrays.stream(employees)
                .collect(Collectors.toMap(Employee::getEmployeeId, Function.identity()));

        lenient().when(employeeService.read(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            Employee employee = byId.get(id);
            if (employee == null) {
                throw new EmployeeNotFoundException(id);
            }
            return employee;
        });

        lenient().when(employeeRepository.findByEmployeeIdIn(anyCollection())).thenAnswer(invocation -> {
            Collection<String> ids = invocation.getArgument(0);
            return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        });
    }

    // Direct reports are id-only stubs, matching how they are stored; no reports means null, like the seed data
    private static Employee employee(String id, String... reportIds) {
        List<Employee> directReports = reportIds.length == 0 ? null : Arrays.stream(reportIds)
                .map(reportId -> Employee.builder().employeeId(reportId).build())
                .collect(Collectors.toCollection(ArrayList::new));

        return Employee.builder()
                .employeeId(id)
                .directReports(directReports)
                .build();
    }
}
