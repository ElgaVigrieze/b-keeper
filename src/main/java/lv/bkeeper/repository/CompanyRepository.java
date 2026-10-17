package lv.bkeeper.repository;

import lv.bkeeper.domain.Company;
import org.springframework.data.repository.CrudRepository;

public interface CompanyRepository extends CrudRepository<Company, Integer> {}
