// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.catalog;

import android.content.Context;
import java.io.IOException;
import ru.playsoftware.j2meloader.R;

/** User-facing recovery text is separate from the original technical diagnostic. */
class LibraryTransferFailure extends IOException {
    private final int resource;
    private final Object[] arguments;

    LibraryTransferFailure(int resource, String diagnostic, Throwable cause, Object[] arguments) {
        super(diagnostic, cause);
        this.resource=resource; this.arguments=arguments.clone();
    }
    static LibraryTransferFailure of(int resource, String diagnostic, Object... arguments) {
        return new LibraryTransferFailure(resource,diagnostic,null,arguments);
    }
    static LibraryTransferFailure caused(int resource, String diagnostic, Throwable cause, Object... arguments) {
        return new LibraryTransferFailure(resource,diagnostic,cause,arguments);
    }
    static String message(Context context, Throwable error) {
        if(error instanceof java.util.concurrent.CancellationException)
            return context.getString(R.string.transfer_cancelled);
        if(error instanceof LibraryTransferFailure) {
            LibraryTransferFailure failure=(LibraryTransferFailure)error;
            Object[] arguments=failure.arguments.clone();
            for(int i=0;i<arguments.length;i++) if(arguments[i] instanceof Throwable)
                arguments[i]=message(context,(Throwable)arguments[i]);
            return context.getString(failure.resource,arguments);
        }
        return error.getMessage()==null?context.getString(R.string.transfer_read_failed):error.getMessage();
    }
}
