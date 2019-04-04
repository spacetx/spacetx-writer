package spacetx;

import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import org.kohsuke.args4j.CmdLineException;

/**
 * Errors produced throughout the code-base.
 */
public enum Errors {

    doesNotExist(1, "input does not exist (%s)"),
    usage(2, "DEFAULT FAILURE CODE"),
    outputExists(3, "output location already exists! (%s)"),
    multipleImages(4,"%s contains multiple images (count=%d). Please choose one."),
    fovIsPositive(5, "FOV must be a greater than or equal to 0 (%d)"),
    tooManyPlates(6, "Too many plates found (count=%d)"),
    tooManyWells(7, "Too many wells found (count=%d)"),
    singleScreening(8, "only a single screening fileset is supported"),
    patternFiles(9, "pattern files must end in '.pattern'"),
    needAction(10, "one of --output, --info, --guess required"),
    unknownFormat(11,"unknown format: %s" );

    public final int rc;

    public final String msg;

    Errors(int rc, String msg) {
        this.rc = rc;
        this.msg = msg;
    }

    public void raise(Object...args) throws UsageException {
        throw new UsageException(rc, String.format(msg, args));
    }

    /*
     * Allows passing a return code for CLI failures.
     */
    public static class UsageException extends CmdLineException {

        final int rc;

        UsageException(int rc, String message) {
            super(message);
            this.rc = rc;
        }
    }

}
