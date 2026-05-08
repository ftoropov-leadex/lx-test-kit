package com.apiframework.testsupport.network;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Utility methods for network failure detection used by the retry subsystem.
 */
public final class NetworkAwareMethodListener {

    /**
     * Walks the cause chain looking for network connectivity exceptions.
     * Used by {@link com.apiframework.testsupport.retry.FrameworkRetryAnalyzer}.
     */
    public static boolean hasNetworkCause(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ConnectException
                || current instanceof SocketTimeoutException
                || current instanceof UnknownHostException
                || current instanceof NoRouteToHostException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Finds the root cause message by walking to the bottom of the cause chain.
     */
    public static String rootCauseMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
