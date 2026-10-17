package lv.bkeeper.repository;

import lv.bkeeper.domain.Employee;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface EmployeeRepository extends CrudRepository<Employee, Long> {
    List<Employee> findByActiveTrue();

    @Query("SELECT * FROM employee ORDER BY last_name, first_name")
    List<Employee> findAllOrderByName();
}
