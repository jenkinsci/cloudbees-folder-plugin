package com.cloudbees.hudson.plugins.folder.computed;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class DeletingChildrenException extends IOException {

    private List<IOException> exceptions;

    public DeletingChildrenException(List<IOException> exceptions) {
        this.exceptions = exceptions;
    }

    @Override
    public synchronized Throwable getCause() {
        // It's only created if it has exceptions
        return this.exceptions.get(0);
    }

    @Override
    public String getMessage() {
        return "Impossible to delete some children when computing the folder. The first errors are: \n\t" +
                String.join("\n\t", exceptions.stream().limit(3).map(e -> e.getMessage()).collect(Collectors.toList()));
    }
}
