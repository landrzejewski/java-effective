package pl.training.bank.domain;

import pl.training.bank.domain.model.AccountNumber;
import pl.training.bank.domain.model.Money;
import pl.training.bank.domain.model.Operation;
import pl.training.bank.domain.model.Operation.AccountCreated;
import pl.training.bank.domain.model.Operation.Deposit;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public final class Operations {

    private final OperationRepository operationRepository;

    public Operations(final OperationRepository operationRepository) {
        this.operationRepository = operationRepository;
    }

    public void record(final Operation operation) {
        operationRepository.save(operation);
    }

    public List<Operation> all() {
        return operationRepository.findAll();
    }

    public List<Operation> forAccount(final AccountNumber number) {
        return operationRepository.findAll().stream()
                .filter(operation -> operation.number().equals(number))
                .collect(toList());
    }

    public List<Operation> between(final LocalDateTime from, final LocalDateTime to) {
        return operationRepository.findAll().stream()
                .filter(operation -> !operation.timestamp().isBefore(from) && !operation.timestamp().isAfter(to))
                .collect(toList());
    }

    public List<Operation> on(final LocalDate day) {
        return operationRepository.findAll().stream()
                .filter(operation -> operation.timestamp().toLocalDate().equals(day))
                .collect(toList());
    }

    public Map<LocalDate, List<Operation>> groupByDay() {
        return operationRepository.findAll().stream()
                .collect(groupingBy(operation -> operation.timestamp().toLocalDate()));
    }

    public Optional<Period> accountAge(final AccountNumber number, final LocalDate today) {
        return operationRepository.findAll().stream()
                .filter(operation -> operation instanceof AccountCreated)
                .filter(operation -> operation.number().equals(number))
                .findFirst()
                .map(operation -> Period.between(operation.timestamp().toLocalDate(), today));
    }

    public Optional<Duration> activitySpan(final AccountNumber number) {
        var timestamps = operationRepository.findAll().stream()
                .filter(operation -> operation.number().equals(number))
                .map(Operation::timestamp)
                .toList();
        if (timestamps.isEmpty()) {
            return Optional.empty();
        }
        var first = timestamps.stream().min(Comparator.naturalOrder()).orElseThrow();
        var last = timestamps.stream().max(Comparator.naturalOrder()).orElseThrow();
        return Optional.of(Duration.between(first, last));
    }

    public Money totalDeposited(final AccountNumber number, final Currency currency) {
        return operationRepository.findAll().stream()
                .filter(operation -> operation.number().equals(number))
                .filter(operation -> operation instanceof Deposit)
                .map(operation -> ((Deposit) operation).amount())
                .filter(money -> money.hasCurrency(currency))
                .reduce(new Money(ZERO, currency), Money::add);
    }

    public List<Operation> statement(final AccountNumber number, final LocalDate from, final LocalDate to) {
        return operationRepository.findAll().stream()
                .filter(operation -> operation.number().equals(number))
                .filter(operation -> {
                    var day = operation.timestamp().toLocalDate();
                    return !day.isBefore(from) && !day.isAfter(to);
                })
                .sorted(Comparator.comparing(Operation::timestamp))
                .collect(toList());
    }

}
