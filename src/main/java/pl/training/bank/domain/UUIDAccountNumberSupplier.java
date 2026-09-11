package pl.training.bank.domain;

import pl.training.bank.domain.model.AccountNumber;

import java.util.UUID;
import java.util.function.Supplier;

public final class UUIDAccountNumberSupplier implements Supplier<AccountNumber> {

    @Override
    public AccountNumber get() {
        return new AccountNumber(UUID.randomUUID().toString());
    }

}
