package com.fitsupplepos.dao;

import com.fitsupplepos.model.User;
import org.hibernate.query.Query;

import java.util.Optional;

public class UserDao extends GenericDao<User, Long> {

    public UserDao() {
        super(User.class);
    }

    public Optional<User> findByUsername(String username) {
        return query(session -> {
            Query<User> q = session.createQuery(
                    "from User where lower(username) = lower(:username)", User.class);
            q.setParameter("username", username);
            return q.uniqueResultOptional();
        });
    }

    public boolean anyUserExists() {
        return count() > 0;
    }
}
