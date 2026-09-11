package pl.training.bank.domain;

import pl.training.bank.domain.model.Operation;

import java.util.List;

public interface OperationRepository {

    void save(Operation operation);

    List<Operation> findAll();

}
