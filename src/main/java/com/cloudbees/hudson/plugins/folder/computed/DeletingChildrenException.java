package com.cloudbees.hudson.plugins.folder.computed;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class DeletingChildrenException extends RuntimeException {

    private List<IOException> exceptions;

    public DeletingChildrenException(List<IOException> exceptions) {
        this.exceptions = exceptions;
    }

    @Override
    public String getMessage() {
        return "Impossible to delete some children when computing the folder: \n" +
                String.join("\n", exceptions.stream().map(e -> e.getMessage()).collect(Collectors.toList()));
    }
}
