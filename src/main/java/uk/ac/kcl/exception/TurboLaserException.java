package uk.ac.kcl.exception;

/**
 * Created by rich on 16/06/16.
 */
public class TurboLaserException extends RuntimeException {
    public TurboLaserException(String message, Throwable cause,
                               boolean enableSuppression, boolean writableStackTrace){
        super(message, cause, enableSuppression, writableStackTrace);

    }
}