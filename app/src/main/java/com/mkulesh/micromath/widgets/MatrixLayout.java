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
package com.mkulesh.micromath.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;

import com.mkulesh.micromath.formula.TermField;
import com.mkulesh.micromath.properties.MatrixProperties;
import com.mkulesh.micromath.undo.FormulaState;

import java.util.ArrayList;

import androidx.annotation.NonNull;

public class MatrixLayout extends TableLayout
{
    public interface TermCreationListener
    {
        TermField onTermCreation(int row, int col, final CustomLayout layout, final CustomEditText text);
    }

    static final class ElementTag
    {
        final int row;
        final int col;
        final int idx;

        ElementTag(int r, int c, int i)
        {
            row = r;
            col = c;
            idx = i;
        }
    }

    private final MatrixProperties dim = new MatrixProperties();
    private final ArrayList<TermField> fields = new ArrayList<>();

    /*--------------------------------------------------------*
     * Creating
     *--------------------------------------------------------*/

    public MatrixLayout(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public MatrixLayout(Context context)
    {
        super(context);
    }

    @Override
    public int getBaseline()
    {
        int height = getPaddingTop();
        for (int row = 0; row < getChildCount(); row++)
        {
            final View child = getChildAt(row);
            height += child.getMeasuredHeight();
        }
        height += getPaddingBottom();
        return height / 2;
    }

    public void resize(int rows, int cols, int cellLayoutId, final TermCreationListener listener, ScaledDimensions dimen)
    {
        if (dim.rows == rows && dim.cols == cols)
        {
            return;
        }
        dim.rows = rows;
        dim.cols = cols;

        removeAllViews();
        fields.clear();

        final TableLayout.LayoutParams tableParams = new TableLayout.LayoutParams(
                TableLayout.LayoutParams.WRAP_CONTENT, TableLayout.LayoutParams.WRAP_CONTENT);

        final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        final int horPadding = dimen.get(ScaledDimensions.Type.MATRIX_COLUMN_PADDING);
        final int vertPadding = dimen.get(ScaledDimensions.Type.VERT_TERM_PADDING);

        for (int row = 0; row < dim.rows; row++)
        {
            final TableRow tableRow = new TableRow(getContext());
            tableRow.setLayoutParams(tableParams); // TableLayout is the parent view
            addView(tableRow);

            for (int col = 0; col < dim.cols; col++)
            {
                if (inflater != null)
                {
                    inflater.inflate(cellLayoutId, tableRow);
                }
            }

            if (tableRow.getChildCount() > 0)
            {
                tableRow.setBaselineAligned(true);
                tableRow.setBaselineAlignedChildIndex(0);
            }

            for (int col = 0; col < tableRow.getChildCount(); col++)
            {
                final CustomLayout layout = (CustomLayout) tableRow.getChildAt(col);
                if (layout != null && layout.getChildCount() > 0)
                {
                    layout.setPadding(horPadding, vertPadding, horPadding, vertPadding);
                    final CustomEditText text = (CustomEditText) layout.getChildAt(0);
                    text.setTag(new ElementTag(row, col, fields.size()));
                    final TermField field = listener.onTermCreation(row, col, layout, text);
                    if (field != null)
                    {
                        updateTextSize(field, dimen);
                        fields.add(field);
                    }
                }
            }
        }

        setPadding(0, 0, 0, 0);
        setBaselineAligned(true);
        setBaselineAlignedChildIndex(dim.rows > 1 ? dim.rows / 2 : 0);
    }

    public MatrixProperties getDim()
    {
        return dim;
    }

    public ArrayList<TermField> getTerms()
    {
        return fields;
    }

    public TermField getTerm(int row, int col)
    {
        for (TermField field : fields)
        {
            if (isCell(field.getEditText()))
            {
                ElementTag tag = (ElementTag) field.getEditText().getTag();
                if (tag.row == row && tag.col == col)
                {
                    return field;
                }
            }
        }
        return null;
    }

    public void setText(int row, int col, String text)
    {
        final TermField cell = getTerm(row, col);
        if (cell != null)
        {
            cell.setText(text);
        }
    }

    public void updateTextSize(ScaledDimensions dimen)
    {
        for (TermField field : fields)
        {
            updateTextSize(field, dimen);
        }
    }

    private void updateTextSize(final TermField field, final ScaledDimensions dimen)
    {
        field.updateTextSize();
        if (field.isTerm())
        {
            final int horPadding = dimen.get(ScaledDimensions.Type.MATRIX_COLUMN_PADDING);
            final int vertPadding = dimen.get(ScaledDimensions.Type.VERT_TERM_PADDING);
            field.getLayout().setPadding(horPadding, vertPadding, horPadding, vertPadding);
        }
        else
        {
            field.getEditText().updateTextSize(dimen, 0,
                    ScaledDimensions.Type.MATRIX_COLUMN_PADDING);
        }
    }

    public void updateTextColor()
    {
        for (TermField field : fields)
        {
            field.updateTextColor();
        }
    }

    private boolean isCell(CustomEditText c)
    {
        return c != null && c.getTag() != null && c.getTag() instanceof ElementTag;
    }

    public void setText(String s, ScaledDimensions dimen)
    {
        for (TermField field : fields)
        {
            field.setText(s);
            field.getEditText().updateTextSize(dimen, 0, ScaledDimensions.Type.MATRIX_COLUMN_PADDING);
        }
    }

    @NonNull
    public FormulaState[][] getTermState()
    {
        final FormulaState[][] state = new FormulaState[getDim().rows][getDim().cols];
        for (int row = 0; row < getDim().rows; row++)
        {
            for (int col = 0; col < getDim().cols; col++)
            {
                final TermField cell = getTerm(row, col);
                if (cell != null)
                {
                    state[row][col] = cell.getState();
                }
            }
        }
        return state;
    }

    public void setTermState(@NonNull final FormulaState[][] state)
    {
        for (int row = 0; row < Math.min(getDim().rows, state.length); row++)
        {
            for (int col = 0; col < Math.min(getDim().cols, state[row].length); col++)
            {
                final TermField cell = getTerm(row, col);
                if (cell != null && state[row][col] != null)
                {
                    cell.undo(state[row][col]);
                }
            }
        }
    }
}
