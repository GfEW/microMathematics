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
package com.mkulesh.micromath.properties;

import android.os.Parcel;
import android.os.Parcelable;

import com.mkulesh.micromath.formula.FormulaList;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.util.Locale;

public class TextProperties implements Parcelable
{
    private static final String XML_PROP_TEXT_STYLE = "textStyle";
    private static final String XML_PROP_NUMBERING = "numbering";

    public enum TextStyle
    {
        CHAPTER,
        SECTION,
        SUBSECTION,
        SUBSUBSECTION,
        TEXT_BODY
    }

    // state- and XML-related attributes
    private static final TextStyle DEFAULT_TEXT_STYLE = TextStyle.TEXT_BODY;
    public TextStyle textStyle = DEFAULT_TEXT_STYLE;

    private static final boolean DEFAULT_NUMBERING = false;
    public boolean numbering = DEFAULT_NUMBERING;

    /**
     * Parcelable interface
     */
    private TextProperties(Parcel in)
    {
        super();
        readFromParcel(in);
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeString(textStyle.toString());
        dest.writeString(String.valueOf(numbering));
    }

    private void readFromParcel(Parcel in)
    {
        textStyle = TextStyle.valueOf(in.readString());
        numbering = Boolean.parseBoolean(in.readString());
    }

    public static final Parcelable.Creator<TextProperties> CREATOR = new Parcelable.Creator<TextProperties>()
    {
        @Override
        public TextProperties createFromParcel(Parcel in)
        {
            return new TextProperties(in);
        }

        @Override
        public TextProperties[] newArray(int size)
        {
            return new TextProperties[size];
        }
    };

    /**
     * Default constructor
     */
    public TextProperties()
    {
        // empty
    }

    public void assign(TextProperties a)
    {
        textStyle = a.textStyle;
        numbering = a.numbering;
    }

    public void readFromXml(XmlPullParser parser)
    {
        String attr = parser.getAttributeValue(null, XML_PROP_TEXT_STYLE);
        if (attr != null)
        {
            try
            {
                textStyle = TextStyle.valueOf(attr.toUpperCase(Locale.ENGLISH));
            }
            catch (Exception e)
            {
                // nothing to do
            }
        }
        attr = parser.getAttributeValue(null, XML_PROP_NUMBERING);
        if (attr != null)
        {
            numbering = Boolean.parseBoolean(attr);
        }
    }

    public void writeToXml(XmlSerializer serializer) throws Exception
    {
        if (textStyle != DEFAULT_TEXT_STYLE)
        {
            serializer.attribute(FormulaList.XML_NS, XML_PROP_TEXT_STYLE,
                    textStyle.toString().toLowerCase(Locale.ENGLISH));
        }
        if (numbering != DEFAULT_NUMBERING)
        {
            serializer.attribute(FormulaList.XML_NS, XML_PROP_NUMBERING, String.valueOf(numbering));
        }
    }

    public int getDepth()
    {
        return isHeader() ? textStyle.ordinal() - TextStyle.SUBSUBSECTION.ordinal() : 0;
    }

    public boolean isHeader()
    {
        return textStyle != TextStyle.TEXT_BODY;
    }

    public static int[] getInitialNumber()
    {
        final int[] initialNumber = new int[TextStyle.values().length];
        for (int i = 0; i < initialNumber.length; i++)
        {
            initialNumber[i] = 0;
        }
        return initialNumber;
    }
}
