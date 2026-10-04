package com.shelter.auth.exception;

public class UserBlockedException extends RuntimeException {

    public UserBlockedException() {
        super("Учётная запись заблокирована");
    }
}
