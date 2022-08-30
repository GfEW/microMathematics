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
package com.mkulesh.micromath.formula.terms;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.mkulesh.micromath.formula.CalculaterTask;
import com.mkulesh.micromath.formula.CalculaterTask.CancelException;
import com.mkulesh.micromath.formula.FormulaTerm;
import com.mkulesh.micromath.formula.Palette;
import com.mkulesh.micromath.formula.PaletteButton;
import com.mkulesh.micromath.formula.TermField;
import com.mkulesh.micromath.formula.TermField.BracketsType;
import com.mkulesh.micromath.math.CalculatedValue;
import com.mkulesh.micromath.plus.R;
import com.mkulesh.micromath.widgets.CustomEditText;
import com.mkulesh.micromath.widgets.CustomTextView;

import java.util.Locale;

public class Operators extends FormulaTerm
{
    public TermTypeIf.GroupType getGroupType()
    {
        return TermTypeIf.GroupType.OPERATORS;
    }

    /**
     * Supported operators
     */
    public enum OperatorType implements TermTypeIf
    {
        PLUS(R.string.formula_operator_plus,
                R.drawable.p_operator_plus,
                R.string.math_operator_plus,
                true),
        MINUS(R.string.formula_operator_minus,
                R.drawable.p_operator_minus,
                R.string.math_operator_minus,
                true),
        MULT(R.string.formula_operator_mult,
                R.drawable.p_operator_mult,
                R.string.math_operator_mult,
                false),
        DIVIDE(R.string.formula_operator_divide,
                R.drawable.p_operator_divide,
                R.string.math_operator_divide,
                false),
        DIVIDE_SLASH(R.string.formula_operator_divide_slash,
                R.drawable.p_operator_divide_slash,
                R.string.math_operator_divide_slash,
                false);

        private final int shortCutId;
        private final int imageId;
        private final int descriptionId;
        private final String lowerCaseName;
        private final boolean skipShortcutInNumeric;

        OperatorType(int shortCutId, int imageId, int descriptionId, boolean skipShortcutInNumeric)
        {
            this.shortCutId = shortCutId;
            this.imageId = imageId;
            this.descriptionId = descriptionId;
            this.lowerCaseName = name().toLowerCase(Locale.ENGLISH);
            this.skipShortcutInNumeric = skipShortcutInNumeric;
        }

        public GroupType getGroupType()
        {
            return GroupType.OPERATORS;
        }

        public int getShortCutId()
        {
            return shortCutId;
        }

        public boolean isSkipShortcutInNumeric()
        {
            return skipShortcutInNumeric;
        }

        public int getImageId()
        {
            return imageId;
        }

        public int getDescriptionId()
        {
            return descriptionId;
        }

        public String getLowerCaseName()
        {
            return lowerCaseName;
        }

        public int getBracketId()
        {
            return Palette.NO_BUTTON;
        }

        public boolean isEnabled(CustomEditText field)
        {
            return true;
        }

        public PaletteButton.Category getPaletteCategory()
        {
            return PaletteButton.Category.CONVERSION;
        }

        public FormulaTerm createTerm(
                TermField termField, LinearLayout layout, String text, int textIndex, Object par) throws Exception
        {
            return new Operators(this, termField, layout, text, textIndex);
        }
    }

    /**
     * Private attributes
     */
    private TermField leftTerm = null, rightTerm = null;


    // Attention: this is not thread-safety declaration!
    private final CalculatedValue fVal = new CalculatedValue(), gVal = new CalculatedValue(),
            fDer = new CalculatedValue(), gDer = new CalculatedValue();

    /*--------------------------------------------------------*
     * Constructors
     *--------------------------------------------------------*/

    private Operators(OperatorType type, TermField owner, LinearLayout layout, String s, int idx) throws Exception
    {
        super(owner, layout);
        termType = type;
        onCreate(s, idx, owner.bracketsType);
    }

    /*--------------------------------------------------------*
     * GUI constructors to avoid lint warning
     *--------------------------------------------------------*/

    public Operators(Context context)
    {
        super();
    }

    public Operators(Context context, AttributeSet attrs)
    {
        super();
    }

    /*--------------------------------------------------------*
     * Common getters
     *--------------------------------------------------------*/

    public OperatorType getOperatorType()
    {
        return (OperatorType) termType;
    }

    /*--------------------------------------------------------*
     * Re-implementation for methods for FormulaBase and FormulaTerm superclass's
     *--------------------------------------------------------*/

    @Override
    public CalculatedValue.ValueType getValue(CalculaterTask thread, CalculatedValue outValue) throws CancelException
    {
        if (termType != null && leftTerm != null && rightTerm != null)
        {
            leftTerm.getValue(thread, fVal);
            rightTerm.getValue(thread, gVal);
            switch (getOperatorType())
            {
            case PLUS:
                return outValue.add(fVal, gVal);
            case MINUS:
                return outValue.subtract(fVal, gVal);
            case MULT:
                return outValue.multiply(fVal, gVal);
            case DIVIDE:
            case DIVIDE_SLASH:
                return outValue.divide(fVal, gVal);
            }
        }
        return outValue.invalidate(CalculatedValue.ErrorType.TERM_NOT_READY);
    }

    @Override
    public DifferentiableType isDifferentiable(String var)
    {
        final int dGrad = Math.min(leftTerm.isDifferentiable(var).ordinal(), rightTerm.isDifferentiable(var).ordinal());
        return DifferentiableType.values()[dGrad];
    }

    @Override
    public CalculatedValue.ValueType getDerivativeValue(String var, CalculaterTask thread, CalculatedValue outValue)
            throws CancelException
    {
        if (termType != null && leftTerm != null && rightTerm != null)
        {
            leftTerm.getDerivativeValue(var, thread, fDer);
            rightTerm.getDerivativeValue(var, thread, gDer);
            switch (getOperatorType())
            {
            case PLUS:
                return outValue.add(fDer, gDer);
            case MINUS:
                return outValue.subtract(fDer, gDer);
            case MULT:
            {
                leftTerm.getValue(thread, fVal);
                rightTerm.getValue(thread, gVal);
                fDer.multiply(fDer, gVal);
                fVal.multiply(fVal, gDer);
                return outValue.add(fDer, fVal);
            }
            case DIVIDE:
            case DIVIDE_SLASH:
            {
                leftTerm.getValue(thread, fVal);
                rightTerm.getValue(thread, gVal);
                fDer.multiply(fDer, gVal);
                fVal.multiply(fVal, gDer);
                gDer.subtract(fDer, fVal);
                fDer.multiply(gVal, gVal);
                return outValue.divide(gDer, fDer);
            }
            }
        }
        return outValue.invalidate(CalculatedValue.ErrorType.TERM_NOT_READY);
    }

    @Override
    protected CustomTextView initializeSymbol(CustomTextView v)
    {
        if (v.getText() != null)
        {
            String t = v.getText().toString();
            if (t.equals(getContext().getResources().getString(R.string.formula_operator_key)))
            {
                switch (getOperatorType())
                {
                case PLUS:
                    v.prepare(CustomTextView.SymbolType.PLUS, getFormulaRoot().getFormulaList().getActivity(), this);
                    v.setText("..");
                    break;
                case MINUS:
                    v.prepare(CustomTextView.SymbolType.MINUS, getFormulaRoot().getFormulaList().getActivity(), this);
                    v.setText("..");
                    break;
                case MULT:
                    v.prepare(CustomTextView.SymbolType.MULT, getFormulaRoot().getFormulaList().getActivity(), this);
                    v.setText(".");
                    break;
                case DIVIDE:
                    v.prepare(CustomTextView.SymbolType.HOR_LINE, getFormulaRoot().getFormulaList().getActivity(), this);
                    v.setText("_");
                    break;
                case DIVIDE_SLASH:
                    v.prepare(CustomTextView.SymbolType.SLASH, getFormulaRoot().getFormulaList().getActivity(), this);
                    v.setText("_");
                    break;
                }
            }
            else if (t.equals(getContext().getResources().getString(R.string.formula_left_bracket_key)))
            {
                v.prepare(CustomTextView.SymbolType.LEFT_BRACKET, getFormulaRoot().getFormulaList().getActivity(), this);
                v.setText("."); // this text defines view width/height
            }
            else if (t.equals(getContext().getResources().getString(R.string.formula_right_bracket_key)))
            {
                v.prepare(CustomTextView.SymbolType.RIGHT_BRACKET, getFormulaRoot().getFormulaList().getActivity(),
                        this);
                v.setText("."); // this text defines view width/height
            }
        }
        return v;
    }

    @Override
    protected CustomEditText initializeTerm(CustomEditText v, LinearLayout l)
    {
        if (v.getText() != null)
        {
            if (v.getText().toString().equals(getContext().getResources().getString(R.string.formula_left_term_key)))
            {
                final boolean addDepth = (termType == OperatorType.DIVIDE);
                leftTerm = addTerm(getFormulaRoot(), l, v, this, addDepth);
            }
            if (v.getText().toString().equals(getContext().getResources().getString(R.string.formula_right_term_key)))
            {
                final int addDepth = (termType == OperatorType.DIVIDE) ? 1 : 0;
                rightTerm = addTerm(getFormulaRoot(), l, -1, v, this, addDepth);
            }
        }
        return v;
    }

    /*--------------------------------------------------------*
     * Implementation for methods for FormulaChangeIf interface
     *--------------------------------------------------------*/

    @Override
    public void onDelete(CustomEditText owner)
    {
        if (parentField != null)
        {
            TermField t = findTerm(owner);
            TermField r = null;
            if (t != null)
            {
                r = (t == leftTerm) ? rightTerm : leftTerm;
            }
            parentField.onTermDelete(removeElements(), r);
        }
        getFormulaRoot().getFormulaList().onManualInput();
    }

    /*--------------------------------------------------------*
     * FormulaTermOperator-specific methods
     *--------------------------------------------------------*/

    /**
     * Procedure creates the formula layout
     */
    private void onCreate(String s, int idx, BracketsType bracketsType) throws Exception
    {
        switch (getOperatorType())
        {
        case PLUS:
        case MINUS:
            useBrackets = bracketsType != BracketsType.NEVER;
            inflateElements(useBrackets ? R.layout.formula_operator_hor_brackets : R.layout.formula_operator_hor, true);
            break;
        case MULT:
        case DIVIDE_SLASH:
            useBrackets = bracketsType == BracketsType.ALWAYS;
            inflateElements(useBrackets ? R.layout.formula_operator_hor_brackets : R.layout.formula_operator_hor, true);
            break;
        case DIVIDE:
            useBrackets = bracketsType == BracketsType.ALWAYS;
            inflateElements(useBrackets ? R.layout.formula_operator_vert_brackets : R.layout.formula_operator_vert,
                    true);
            break;
        }
        initializeElements(idx);
        if (leftTerm == null || rightTerm == null)
        {
            throw new Exception("cannot initialize operator terms");
        }
        // set texts for left and right parts
        splitIntoTerms(s, termType, false);
        // disable brackets of child terms in some cases
        switch (getOperatorType())
        {
        case DIVIDE:
        case PLUS:
            leftTerm.bracketsType = BracketsType.NEVER;
            rightTerm.bracketsType = BracketsType.NEVER;
            break;
        case DIVIDE_SLASH:
            leftTerm.bracketsType = BracketsType.IFNECESSARY;
            rightTerm.bracketsType = BracketsType.ALWAYS;
            break;
        case MULT:
            leftTerm.bracketsType = BracketsType.IFNECESSARY;
            rightTerm.bracketsType = BracketsType.IFNECESSARY;
            break;
        case MINUS:
            leftTerm.bracketsType = BracketsType.NEVER;
            rightTerm.bracketsType = BracketsType.IFNECESSARY;
            break;
        }
    }
}
