package lv.bkeeper.repository;

import lv.bkeeper.domain.Customer;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends CrudRepository<Customer, Long> {

    List<Customer> findByActiveTrueOrderByName();

    @Query("SELECT * FROM customer WHERE active = 1 AND vat_number = :vat LIMIT 1")
    Optional<Customer> findByVatNumberActive(@Param("vat") String vatNumber);

    @Query("SELECT * FROM customer WHERE active = 1 AND reg_number = :reg LIMIT 1")
    Optional<Customer> findByRegNumberActive(@Param("reg") String regNumber);
}
