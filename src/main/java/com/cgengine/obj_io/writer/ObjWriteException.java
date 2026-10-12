package com.cgengine.obj_io.writer;
/** Controlled failure reported by OBJ serialization. */
public final class ObjWriteException extends RuntimeException {
    public ObjWriteException(String message, Throwable cause) { super(message, cause); }
}
