package com.careermatch.backend.common.converter;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;

public class PgVectorUserType implements UserType<float[]> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<float[]> returnedClass() {
        return float[].class;
    }

    @Override
    public boolean equals(float[] x, float[] y) {
        return Arrays.equals(x, y);
    }

    @Override
    public int hashCode(float[] x) {
        return Arrays.hashCode(x);
    }

    @Override
    public float[] nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        Object val = rs.getObject(position);
        if (val == null) {
            return null;
        }
        String clean = val.toString().trim();
        if (clean.startsWith("[")) clean = clean.substring(1);
        if (clean.endsWith("]")) clean = clean.substring(0, clean.length() - 1);
        clean = clean.trim();
        if (clean.isEmpty()) return new float[0];
        String[] parts = clean.split(",");
        float[] res = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            res[i] = Float.parseFloat(parts[i].trim());
        }
        return res;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, float[] value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (value == null || value.length == 0) {
            st.setNull(index, Types.OTHER, "vector");
        } else {
            PGobject obj = new PGobject();
            obj.setType("vector");
            obj.setValue(PgVectorConverter.toVectorString(value));
            st.setObject(index, obj);
        }
    }

    @Override
    public float[] deepCopy(float[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(float[] value) {
        return value;
    }

    @Override
    public float[] assemble(Serializable cached, Object owner) {
        return (float[]) cached;
    }

    @Override
    public float[] replace(float[] detached, float[] managed, Object owner) {
        return detached;
    }
}
