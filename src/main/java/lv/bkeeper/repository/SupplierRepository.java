package lv.bkeeper.repository;

import lv.bkeeper.domain.Supplier;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends CrudRepository<Supplier, Long> {

    List<Supplier> findByActiveTrueOrderByName();

    Optional<Supplier> findByVatNumber(String vatNumber);

    @Query("SELECT * FROM supplier WHERE active = 1 AND vat_number = :vat LIMIT 1")
    Optional<Supplier> findByVatNumberActive(@Param("vat") String vatNumber);

    @Query("SELECT * FROM supplier WHERE active = 1 AND reg_number = :reg LIMIT 1")
    Optional<Supplier> findByRegNumber(@Param("reg") String regNumber);
}
