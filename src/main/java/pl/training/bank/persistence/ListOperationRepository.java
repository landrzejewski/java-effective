package pl.training.bank.persistence;

import pl.training.bank.domain.OperationRepository;
import pl.training.bank.domain.model.Operation;

import java.util.ArrayList;
import java.util.List;

public final class ListOperationRepository implements OperationRepository {

    private final List<Operation> operations = new ArrayList<>();

    @Override
    public void save(final Operation operation) {
        operations.add(operation);
    }

    @Override
    public List<Operation> findAll() {
        return List.copyOf(operations);
    }

}
