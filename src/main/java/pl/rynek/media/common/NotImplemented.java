package pl.rynek.media.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Wyjątek zwracany przez endpointy szkieletu, które nie mają jeszcze implementacji (501, RFC 7807).
 */
public final class NotImplemented {

    private NotImplemented() {
    }

    public static ResponseStatusException notImplemented() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Endpoint nie jest jeszcze zaimplementowany");
    }
}
