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
import android.content.res.Resources;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.mkulesh.micromath.formula.BracketParser;
import com.mkulesh.micromath.formula.CalculatableIf;
import com.mkulesh.micromath.formula.CalculaterTask;
import com.mkulesh.micromath.formula.CalculaterTask.CancelException;
import com.mkulesh.micromath.formula.Equation;
import com.mkulesh.micromath.formula.FormulaBase;
import com.mkulesh.micromath.formula.FormulaTerm;
import com.mkulesh.micromath.formula.LinkHolder;
import com.mkulesh.micromath.formula.Palette;
import com.mkulesh.micromath.formula.PaletteButton;
import com.mkulesh.micromath.formula.TermField;
import com.mkulesh.micromath.math.CalculatedValue;
import com.mkulesh.micromath.plus.R;
import com.mkulesh.micromath.widgets.CustomEditText;
import com.mkulesh.micromath.widgets.CustomTextView;

import java.util.ArrayList;
import java.util.Locale;

public class UserFunctions extends FunctionBase
{
    public TermTypeIf.GroupType getGroupType()
    {
        return TermTypeIf.GroupType.USER_FUNCTIONS;
    }

    /**
     * Supported functions
     */
    public enum FunctionType implements UserFunctionIf
    {
        FUNCTION_INDEX(-1, R.drawable.p_function_index, R.string.math_function_index,
                R.string.formula_function_start_index, "content:com.mkulesh.micromath.index"),
        IDENTITY(1, R.drawable.p_function_identity, R.string.math_function_identity,
                R.string.formula_function_start_bracket, null),
        FUNCTION_LINK(-1, Palette.NO_BUTTON, Palette.NO_BUTTON,
                R.string.formula_function_start_bracket, "content:com.mkulesh.micromath.link");

        private final int argNumber;
        private final int imageId;
        private final int descriptionId;
        private final int shortCutId;
        private final String linkObject;
        private final String lowerCaseName;

        FunctionType(int argNumber, int imageId, int descriptionId, int shortCutId, String linkObject)
        {
            this.argNumber = argNumber;
            this.imageId = imageId;
            this.descriptionId = descriptionId;
            this.shortCutId = shortCutId;
            this.linkObject = linkObject;
            this.lowerCaseName = name().toLowerCase(Locale.ENGLISH);
        }

        public GroupType getGroupType()
        {
            return GroupType.USER_FUNCTIONS;
        }

        public int getShortCutId()
        {
            return shortCutId;
        }

        int getArgNumber()
        {
            return argNumber;
        }

        public int getImageId()
        {
            return imageId;
        }

        public int getDescriptionId()
        {
            return descriptionId;
        }

        public String getLinkObject()
        {
            return linkObject;
        }

        public boolean isLink()
        {
            return linkObject != null;
        }

        public String getLowerCaseName()
        {
            return lowerCaseName;
        }

        public int getBracketId()
        {
            return R.string.formula_function_start_bracket;
        }

        public boolean isEnabled(CustomEditText field)
        {
            return true;
        }

        public PaletteButton.Category getPaletteCategory()
        {
            return this == FUNCTION_INDEX ? PaletteButton.Category.INDEX : PaletteButton.Category.CONVERSION;
        }

        public FormulaTerm createTerm(
                TermField termField, LinearLayout layout, String text, int textIndex, Object par) throws Exception
        {
            return new UserFunctions(this, termField, layout, text, textIndex);
        }
    }

    public static final String FUNCTION_ARGS_MARKER = ":";

    private static String getFunctionString(FunctionType t)
    {
        return t.isLink() ? t.getLinkObject() : t.getLowerCaseName();
    }

    /**
     * Private attributes
     */
    private String functionLinkName = "unknown";
    private Equation linkedFunction = null;

    // Attention: this is not thread-safety declaration!
    private final CalculatedValue a0derVal = new CalculatedValue();

    /*--------------------------------------------------------*
     * Constructors
     *--------------------------------------------------------*/

    private UserFunctions(FunctionType type, TermField owner, LinearLayout layout, String s, int idx) throws Exception
    {
        super(owner, layout);
        termType = type;
        onCreate(s, idx);
    }

    /*--------------------------------------------------------*
     * GUI constructors to avoid lint warning
     *--------------------------------------------------------*/

    public UserFunctions(Context context)
    {
        super();
    }

    public UserFunctions(Context context, AttributeSet attrs)
    {
        super();
    }

    /*--------------------------------------------------------*
     * Common getters
     *--------------------------------------------------------*/

    public FunctionType getFunctionType()
    {
        return (FunctionType) termType;
    }

    /*--------------------------------------------------------*
     * Re-implementation for methods for FormulaBase and FormulaTerm superclass's
     *--------------------------------------------------------*/

    @Override
    public String getFunctionLabel()
    {
        switch (getFunctionType())
        {
        case FUNCTION_LINK:
        case FUNCTION_INDEX:
            return functionLinkName;
        case IDENTITY:
            return "";
        default:
            return termType.getLowerCaseName();
        }
    }

    @Override
    public CalculatedValue.ValueType getValue(CalculaterTask thread, CalculatedValue outValue) throws CancelException
    {
        if (termType != null && terms.size() > 0)
        {
            ensureArgValSize();
            for (int i = 0; i < terms.size(); i++)
            {
                terms.get(i).getValue(thread, argVal[i]);
            }
            final CalculatedValue a0 = argVal[0];
            switch (getFunctionType())
            {
            case IDENTITY:
                return outValue.assign(a0);

            case FUNCTION_LINK:
            case FUNCTION_INDEX:
                if (linkedFunction != null && linkedFunction.setArgumentValues(argVal))
                {
                    return linkedFunction.getValue(thread, outValue);
                }
                break;
            }
        }
        return outValue.invalidate(CalculatedValue.ErrorType.TERM_NOT_READY);
    }

    @Override
    public CalculatableIf.DifferentiableType isDifferentiable(String var)
    {
        if (termType == null)
        {
            return CalculatableIf.DifferentiableType.NONE;
        }
        CalculatableIf.DifferentiableType argsProp = CalculatableIf.DifferentiableType.INDEPENDENT;
        for (int i = 0; i < terms.size(); i++)
        {
            final int dGrad = Math.min(argsProp.ordinal(), terms.get(i).isDifferentiable(var).ordinal());
            argsProp = CalculatableIf.DifferentiableType.values()[dGrad];
        }

        CalculatableIf.DifferentiableType retValue = CalculatableIf.DifferentiableType.NONE;
        switch (getFunctionType())
        {
        // for these functions, derivative can be calculated analytically
        case IDENTITY:
            retValue = argsProp;
            break;
        // for this function, derivative depends on the function itself
        case FUNCTION_LINK:
            if (linkedFunction != null)
            {
                final int dGrad = Math.min(argsProp.ordinal(), linkedFunction.isDifferentiable(var).ordinal());
                retValue = CalculatableIf.DifferentiableType.values()[dGrad];
            }
            break;
        // these functions are not differentiable if contain the given argument
        case FUNCTION_INDEX:
            retValue = (argsProp == CalculatableIf.DifferentiableType.INDEPENDENT) ? CalculatableIf.DifferentiableType.INDEPENDENT
                    : CalculatableIf.DifferentiableType.NONE;
            break;
        }
        // set the error code to be displayed
        ErrorCode errorCode = ErrorCode.NO_ERROR;
        if (retValue == CalculatableIf.DifferentiableType.NONE)
        {
            errorCode = ErrorCode.NOT_DIFFERENTIABLE;
        }
        setErrorCode(errorCode, var);
        return retValue;
    }

    @Override
    public CalculatedValue.ValueType getDerivativeValue(String var, CalculaterTask thread, CalculatedValue outValue)
            throws CancelException
    {
        if (termType != null && terms.size() > 0)
        {
            ensureArgValSize();
            for (int i = 0; i < terms.size(); i++)
            {
                terms.get(i).getValue(thread, argVal[i]);
            }
            terms.get(0).getDerivativeValue(var, thread, a0derVal);
            switch (getFunctionType())
            {
            // for these functions, derivative can be calculated analytically
            case IDENTITY:
                return outValue.assign(a0derVal);
            case FUNCTION_LINK:
                if (linkedFunction != null && linkedFunction.setArgumentValues(argVal))
                {
                    if (linkedFunction.getArguments() == null || linkedFunction.getArguments().size() != terms.size())
                    {
                        return outValue.setValue(0.0);
                    }
                    outValue.setValue(0.0);
                    final CalculatedValue tmp = new CalculatedValue();
                    for (int i = 0; i < terms.size(); i++)
                    {
                        final String s = linkedFunction.getArguments().get(i);
                        linkedFunction.getDerivativeValue(s, thread, tmp);
                        if (i > 0)
                        {
                            terms.get(i).getDerivativeValue(var, thread, a0derVal);
                        }
                        tmp.multiply(tmp, a0derVal);
                        if (i == 0)
                        {
                            // For the first term, use assign in oder to set units
                            outValue.assign(tmp);
                        }
                        else
                        {
                            outValue.add(outValue, tmp);
                        }
                    }
                    return outValue.getValueType();
                }
                break;
            // these functions are not differentiable if contain the given argument
            case FUNCTION_INDEX:
                CalculatableIf.DifferentiableType argsProp = CalculatableIf.DifferentiableType.INDEPENDENT;
                for (int i = 0; i < terms.size(); i++)
                {
                    final int dGrad = Math.min(argsProp.ordinal(), terms.get(i).isDifferentiable(var).ordinal());
                    argsProp = CalculatableIf.DifferentiableType.values()[dGrad];
                }
                if (argsProp == CalculatableIf.DifferentiableType.INDEPENDENT)
                {
                    return outValue.setValue(0.0);
                }
                else
                {
                    return outValue.invalidate(CalculatedValue.ErrorType.NOT_A_NUMBER);
                }
            }
        }
        return outValue.invalidate(CalculatedValue.ErrorType.TERM_NOT_READY);
    }

    @Override
    public String getTermCode()
    {
        String t = getFunctionString(getFunctionType());
        if (getFunctionType().isLink())
        {
            t += "." + functionLinkName;
            if (terms.size() > 1)
            {
                t += FUNCTION_ARGS_MARKER + terms.size();
            }
        }
        return t;
    }

    @Override
    public boolean isContentValid(FormulaBase.ValidationPassType type)
    {
        boolean isValid = true;
        switch (type)
        {
        case VALIDATE_SINGLE_FORMULA:
            linkedFunction = null;
            isValid = super.isContentValid(type);
            if (isValid && !parentField.isEquationName() && getFunctionType().isLink())
            {
                Equation f = getFormulaRoot().searchLinkedEquation(functionLinkName, terms.size(), false);
                if (f == null)
                {
                    f = getFormulaRoot().searchLinkedEquation(functionLinkName, Equation.ARG_NUMBER_INTERVAL, false);
                }
                ErrorCode errorCode = ErrorCode.NO_ERROR;
                if (f == null)
                {
                    errorCode = (termType == FunctionType.FUNCTION_LINK) ? ErrorCode.UNKNOWN_FUNCTION
                            : ErrorCode.UNKNOWN_ARRAY;
                    isValid = false;
                }
                else if (f.getId() == getFormulaRoot().getId())
                {
                    errorCode = ErrorCode.RECURSIVE_CALL;
                    isValid = false;
                }
                else if (termType == FunctionType.FUNCTION_LINK && f.isArray())
                {
                    errorCode = ErrorCode.NOT_A_FUNCTION;
                    isValid = false;
                }
                else if (termType == FunctionType.FUNCTION_INDEX && !f.isArray() && !f.isInterval())
                {
                    errorCode = ErrorCode.NOT_AN_ARRAY;
                    isValid = false;
                }
                else
                {
                    linkedFunction = f;
                }
                setErrorCode(errorCode, functionLinkName + "[" + terms.size() + "]");
                if (getFormulaRoot() instanceof LinkHolder && linkedFunction != null)
                {
                    if (!linkedFunction.isInterval())
                    {
                        ((LinkHolder) getFormulaRoot()).addLinkedEquation(linkedFunction);
                    }
                }
            }
            break;
        case VALIDATE_LINKS:
            isValid = super.isContentValid(type);
            break;
        }
        return isValid;
    }

    @Override
    protected CustomTextView initializeSymbol(CustomTextView v)
    {
        final Resources res = getContext().getResources();
        if (v.getText() != null)
        {
            String t = v.getText().toString();
            if (t.equals(res.getString(R.string.formula_operator_key)))
            {
                v.prepare(CustomTextView.SymbolType.TEXT, getFormulaRoot().getFormulaList().getActivity(), this);
                v.setText(getFunctionLabel());
                functionTerm = v;
            }
            else if (t.equals(res.getString(R.string.formula_left_bracket_key)))
            {
                CustomTextView.SymbolType s = CustomTextView.SymbolType.LEFT_BRACKET;
                v.prepare(s, getFormulaRoot().getFormulaList().getActivity(), this);
                v.setText("."); // this text defines view width/height
            }
            else if (t.equals(res.getString(R.string.formula_right_bracket_key)))
            {
                CustomTextView.SymbolType s = CustomTextView.SymbolType.RIGHT_BRACKET;
                v.prepare(s, getFormulaRoot().getFormulaList().getActivity(), this);
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
            final String val = v.getText().toString();
            if (termType == FunctionType.FUNCTION_INDEX
                    && val.equals(getContext().getResources().getString(R.string.formula_arg_term_key)))
            {
                final TermField t = addTerm(getFormulaRoot(), l, -1, v, this, getArgumentDepth());
                t.bracketsType = TermField.BracketsType.NEVER;
                t.getEditText().setIndexName(parentField != null && parentField.isEquationName());
            }
            else if (termType != FunctionType.FUNCTION_INDEX
                    && val.equals(getContext().getResources().getString(R.string.formula_arg_term_key)))
            {
                final TermField t = addTerm(getFormulaRoot(), l, -1, v, this, 0);
                t.bracketsType = TermField.BracketsType.NEVER;
                if (termType == FunctionType.IDENTITY)
                {
                    t.getEditText().setComparatorEnabled(true);
                }
            }
        }
        return v;
    }

    /*--------------------------------------------------------*
     * Implementation for methods for FormulaChangeIf interface
     *--------------------------------------------------------*/

    @Override
    public boolean isNewTermEnabled()
    {
        return getFunctionType().isLink();
    }

    @Override
    public boolean onNewTerm(TermField owner, String s, boolean requestFocus)
    {
        final String sep = getContext().getResources().getString(R.string.formula_term_separator);
        if (s == null || s.length() == 0 || !s.contains(sep))
        {
            // string does not contains the term separator: can not be processed
            return false;
        }

        // below, we will return true since the string is processed independently from
        // the result
        if (!isNewTermEnabled())
        {
            return true;
        }

        TermField newArg = addArgument(owner, R.layout.formula_function_add_arg, getArgumentDepth());
        if (newArg == null)
        {
            return true;
        }
        newArg.getEditText().setIndexName(parentField != null && parentField.isEquationName());

        updateTextSize();
        if (owner.getText().contains(sep))
        {
            TermField.divideString(s, sep, owner, newArg);
        }
        isContentValid(FormulaBase.ValidationPassType.VALIDATE_SINGLE_FORMULA);
        if (requestFocus)
        {
            newArg.getEditText().requestFocus();
        }
        return true;
    }

    /*--------------------------------------------------------*
     * FormulaTermFunction-specific methods
     *--------------------------------------------------------*/

    private int getArgumentDepth()
    {
        return termType == FunctionType.FUNCTION_INDEX ? 3 : 0;
    }

    /**
     * Procedure creates the formula layout
     */
    private void onCreate(String s, int idx) throws Exception
    {
        int argNumber = getFunctionType().getArgNumber();
        switch (getFunctionType())
        {
        case FUNCTION_LINK:
            functionLinkName = getFunctionLinkName(s, getFunctionType(),
                    R.string.formula_function_start_bracket);
            if (functionLinkName == null)
            {
                throw new Exception("cannot create " + getFunctionType().toString() + " since function name is invalid");
            }
            inflateElements(R.layout.formula_function_named, true);
            argNumber = getArgNumber(s, functionLinkName);
            break;
        case FUNCTION_INDEX:
            functionLinkName = getFunctionLinkName(s, getFunctionType(),
                    R.string.formula_function_start_index);
            if (functionLinkName == null)
            {
                throw new Exception("cannot create " + getFunctionType().toString() + " since function name is invalid");
            }
            inflateElements(R.layout.formula_function_index, true);
            argNumber = getArgNumber(s, functionLinkName);
            s = BracketParser.removeBrackets(getContext(), s, BracketParser.ARRAY_BRACKETS);
            s = BracketParser.removeBrackets(getContext(), s, BracketParser.FUNCTION_BRACKETS);
            if (functionLinkName.equals(s))
            {
                s = "";
            }
            break;
        case IDENTITY:
            inflateElements(R.layout.formula_function_noname, true);
            break;
        }
        initializeElements(idx);
        if (terms.isEmpty())
        {
            throw new Exception("argument list is empty");
        }

        initializeMainLayout();

        // add additional arguments
        while (terms.size() < argNumber)
        {
            TermField newTerm = addArgument(terms.get(terms.size() - 1), R.layout.formula_function_add_arg,
                    getArgumentDepth());
            if (newTerm == null)
            {
                break;
            }
        }
        if (getFunctionType().getArgNumber() > 0 && terms.size() != getFunctionType().getArgNumber())
        {
            throw new Exception("invalid size for argument list");
        }

        final String arg = BracketParser.removeBrackets(getContext(), s, BracketParser.FUNCTION_BRACKETS);
        if (splitIntoTerms(arg, termType, false))
        {
            isContentValid(ValidationPassType.VALIDATE_SINGLE_FORMULA);
        }
    }

    /**
     * Procedure extracts linked function name from given string
     */
    private String getFunctionLinkName(String s, FunctionType f, int bracketId)
    {
        final Resources res = getContext().getResources();
        try
        {
            if (s.contains(res.getString(bracketId)))
            {
                String opCode = res.getString(bracketId);
                return s.substring(0, s.indexOf(opCode)).trim();
            }
            if (s.contains(f.getLinkObject()))
            {
                final String opCode = f.getLinkObject() + ".";
                final String nameAndArgs = s.substring(s.indexOf(opCode) + opCode.length());
                if (nameAndArgs != null && nameAndArgs.length() > 0)
                {
                    final int argsMarker = nameAndArgs.indexOf(FUNCTION_ARGS_MARKER);
                    if (argsMarker > 0)
                    {
                        return nameAndArgs.substring(0, argsMarker);
                    }
                }
                return nameAndArgs;
            }
            final String fName = f.getLowerCaseName()
                    + res.getString(R.string.formula_function_start_bracket);
            if (s.contains(fName))
            {
                return s.replace(fName, "");
            }
        }
        catch (Exception ex)
        {
            // nothing to do
        }
        return null;
    }

    /**
     * Procedure extracts number of arguments from given string
     */
    private int getArgNumber(String s, String functionName)
    {
        try
        {
            String opCode = null;
            for (FunctionType f : FunctionType.values())
            {
                if (f.isLink() && s.contains(f.getLinkObject()))
                {
                    opCode = f.getLinkObject() + ".";
                    break;
                }
            }
            if (opCode != null)
            {
                final String nameAndArgs = s.substring(s.indexOf(opCode) + opCode.length());
                if (nameAndArgs != null && nameAndArgs.length() > 0)
                {
                    final int argsMarker = nameAndArgs.indexOf(FUNCTION_ARGS_MARKER);
                    if (argsMarker > 0)
                    {
                        final String argsStr = nameAndArgs.substring(argsMarker + FUNCTION_ARGS_MARKER.length()
                        );
                        return Integer.parseInt(argsStr);
                    }
                }
                return 1;
            }
            else if (!s.contains(getContext().getResources().getString(R.string.formula_term_separator)))
            {
                final Equation f = getFormulaRoot().searchLinkedEquation(functionName, Equation.ARG_NUMBER_ANY);
                if (f != null)
                {
                    ArrayList<String> args = f.getArguments();
                    if (args != null && !args.isEmpty())
                    {
                        return args.size();
                    }
                }
            }
        }
        catch (Exception ex)
        {
            // nothing to do
        }
        return 1;
    }
}
