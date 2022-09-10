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
package com.mkulesh.micromath.utils;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Build;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ClipboardManager
{
    private static final String CLIPBOARD_LABEL = "com.mkulesh.micromath.clipboard";
    public static final String CLIPBOARD_TERM_OBJECT = "content:com.mkulesh.micromath.term";
    public static final String CLIPBOARD_LIST_OBJECT = "content:com.mkulesh.micromath.list";

    public static boolean isFormulaObject(final String s)
    {
        if (s != null)
        {
            return s.contains(ClipboardManager.CLIPBOARD_TERM_OBJECT)
                    || s.contains(ClipboardManager.CLIPBOARD_LIST_OBJECT);
        }
        return false;
    }

    public static void copyToClipboard(Context context, String text)
    {
        try
        {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText(CLIPBOARD_LABEL, text);
            clipboard.setPrimaryClip(clip);
        }
        catch (Exception e)
        {
            // nothing to do
        }
    }

    public static String readFromClipboard(Context context, boolean textOnly)
    {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context
                .getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = clipboard.getPrimaryClip();
        if (clip != null)
        {
            ClipData.Item item = clip.getItemAt(0);
            if (item != null)
            {
                // If this Item has an explicit textual value, simply return that.
                CharSequence text = item.getText();
                if (text != null)
                {
                    return text.toString();
                }
                if (!textOnly)
                {
                    text = convertToText(context, item);
                }
                if (text != null)
                {
                    return text.toString();
                }
            }
            return null;
        }
        return "";
    }

    private static CharSequence convertToText(Context context, ClipData.Item item)
    {
        // If this Item has a URI value, try using that.
        Uri uri = item.getUri();
        if (uri != null)
        {
            // First see if the URI can be opened as a plain text stream
            // (of any sub-type). If so, this is the best textual
            // representation for it.
            FileInputStream stream = null;
            try
            {
                // Ask for a stream of the desired type.
                AssetFileDescriptor descr = context.getContentResolver().openTypedAssetFileDescriptor(uri, "text/*",
                        null);
                stream = descr.createInputStream();
                InputStreamReader reader;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                {
                    reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                }
                else
                {
                    reader = new InputStreamReader(stream, "UTF-8");
                }

                // Got it... copy the stream into a local string and return it.
                StringBuilder builder = new StringBuilder(128);
                char[] buffer = new char[8192];
                int len;
                while ((len = reader.read(buffer)) > 0)
                {
                    builder.append(buffer, 0, len);
                }
                return builder.toString();
            }
            catch (FileNotFoundException e)
            {
                // Unable to open content URI as text... not really an
                // error, just something to ignore.
            }
            catch (IOException e)
            {
                return e.toString();
            }
            finally
            {
                if (stream != null)
                {
                    try
                    {
                        stream.close();
                    }
                    catch (IOException e)
                    {
                        // empty
                    }
                }
            }

            // If we couldn't open the URI as a stream, then the URI itself
            // probably serves fairly well as a textual representation.
            return uri.toString();
        }

        // Finally, if all we have is an Intent, then we can just turn that
        // into text. Not the most user-friendly thing, but it's something.
        Intent intent = item.getIntent();
        if (intent != null)
        {
            return intent.toUri(Intent.URI_INTENT_SCHEME);
        }

        // Shouldn't get here, but just in case...
        return "";
    }

}
