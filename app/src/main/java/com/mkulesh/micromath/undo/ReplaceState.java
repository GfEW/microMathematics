/*
 * microMathematics - Extended Visual Calculator
 * Copyright (C) 2014-2022 by Mikhail Kulesh
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details. You should have received a copy of the GNU General
 * Public License along with this program.
 */
package com.mkulesh.micromath.undo;

import android.os.Parcel;
import android.os.Parcelable;

import com.mkulesh.micromath.formula.FormulaBase;

import java.util.ArrayList;

/*
 * Class that holds the undo state of replace operation.
 */
public final class ReplaceState implements Parcelable
{
    /*
     * Helper class that holds the undo state of a single deletion.
     */
    public static class EntryState implements Parcelable
    {
        public final FormulaBase.BaseType type;
        public final int formulaId;
        public final Parcelable data;

        EntryState(int formulaId, FormulaBase.BaseType type, Parcelable data)
        {
            super();
            this.formulaId = formulaId;
            this.type = type;
            this.data = data;
        }

        EntryState(Parcel in)
        {
            super();
            formulaId = in.readInt();
            type = FormulaBase.BaseType.values()[in.readInt()];
            data = in.readParcelable(getClass().getClassLoader());
        }

        @Override
        public int describeContents()
        {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags)
        {
            dest.writeInt(formulaId);
            dest.writeInt(type.ordinal());
            dest.writeParcelable(data, flags);
        }

        public static final Parcelable.Creator<EntryState> CREATOR = new Parcelable.Creator<EntryState>()
        {
            @Override
            public EntryState createFromParcel(Parcel in)
            {
                return new EntryState(in);
            }

            @Override
            public EntryState[] newArray(int size)
            {
                return new EntryState[size];
            }
        };
    }

    private final ArrayList<EntryState> entries = new ArrayList<>();

    public ReplaceState()
    {
        super();
    }

    private ReplaceState(Parcel in)
    {
        super();
        in.readTypedList(entries, EntryState.CREATOR);
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeTypedList(entries);
    }

    public ArrayList<EntryState> getEntries()
    {
        return entries;
    }

    public void store(int newFormulaId, FormulaBase prevFormula)
    {
        if (prevFormula != null)
        {
            entries.add(new EntryState(newFormulaId, prevFormula.getBaseType(), prevFormula.onSaveInstanceState()));
        }
        else
        {
            entries.add(new EntryState(newFormulaId, FormulaBase.BaseType.TERM, null));
        }
    }

    public static final Parcelable.Creator<ReplaceState> CREATOR = new Parcelable.Creator<ReplaceState>()
    {
        @Override
        public ReplaceState createFromParcel(Parcel in)
        {
            return new ReplaceState(in);
        }

        @Override
        public ReplaceState[] newArray(int size)
        {
            return new ReplaceState[size];
        }
    };
}
