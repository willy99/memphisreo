package com.memphisreo.security.jwt;

import com.memphisreo.security.AccountIdentity;
import com.memphisreo.security.AccountIdentityRepository;

import java.util.UUID;

public class AccountIdentityTokenRevocationCheck implements TokenRevocationCheck {

    private final AccountIdentityRepository repository;

    public AccountIdentityTokenRevocationCheck(AccountIdentityRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isStillValid(UUID accountIdentityId, int tokenVersionInToken) {
        return repository.findById(accountIdentityId)
                .map(identity -> identity.getStatus() == AccountIdentity.Status.ACTIVE
                        && identity.getTokenVersion() == tokenVersionInToken)
                .orElse(false);
    }
}
