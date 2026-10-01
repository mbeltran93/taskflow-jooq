package com.taskflow.repository;

import com.taskflow.jooq.generated.tables.records.UsersRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.taskflow.jooq.generated.Tables.USERS;

@Repository
public class UserRepository {

    private final DSLContext dsl;

    public UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<UsersRecord> findAll() {
        return dsl.selectFrom(USERS).orderBy(USERS.ID).fetch();
    }

    public Optional<UsersRecord> findById(Long id) {
        return dsl.selectFrom(USERS).where(USERS.ID.eq(id)).fetchOptional();
    }

    public Optional<UsersRecord> findByEmail(String email) {
        return dsl.selectFrom(USERS).where(USERS.EMAIL.eq(email)).fetchOptional();
    }

    public boolean existsByEmail(String email) {
        return dsl.fetchExists(dsl.selectOne().from(USERS).where(USERS.EMAIL.eq(email)));
    }

    public boolean existsById(Long id) {
        return dsl.fetchExists(dsl.selectOne().from(USERS).where(USERS.ID.eq(id)));
    }

    public UsersRecord insert(String name, String email, String passwordHash) {
        return dsl.insertInto(USERS)
                .set(USERS.NAME, name)
                .set(USERS.EMAIL, email)
                .set(USERS.PASSWORD_HASH, passwordHash)
                .returning(USERS.fields())
                .fetchOne();
    }
}
