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
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.mkulesh.micromath.formula.ArgumentHolderIf;
import com.mkulesh.micromath.formula.CalculaterTask;
import com.mkulesh.micromath.formula.CalculaterTask.CancelException;
import com.mkulesh.micromath.formula.FormulaTerm;
import com.mkulesh.micromath.formula.Palette;
import com.mkulesh.micromath.formula.PaletteButton;
import com.mkulesh.micromath.formula.TermField;
import com.mkulesh.micromath.formula.TermField.BracketsType;
import com.mkulesh.micromath.math.CalculatedValue;
import com.mkulesh.micromath.math.CalculatedValue.ValueType;
import com.mkulesh.micromath.plus.R;
import com.mkulesh.micromath.utils.ViewUtils;
import com.mkulesh.micromath.widgets.CustomEditText;
import com.mkulesh.micromath.widgets.CustomTextView;
import com.mkulesh.micromath.widgets.ScaledDimensions;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Locale;

import javax.measure.unit.Unit;

public class SeriesIntegrals extends FormulaTerm implements ArgumentHolderIf
{
    public TermTypeIf.GroupType getGroupType()
    {
        return TermTypeIf.GroupType.SERIES_INTEGRALS;
    }

    /**
     * Supported loop types
     */
    public enum LoopType implements TermTypeIf
    {
        SUMMATION(R.string.formula_loop_summation, R.drawable.p_loop_summation, R.string.math_loop_summation),
        PRODUCT(R.string.formula_loop_product, R.drawable.p_loop_product, R.string.math_loop_product),
        INTEGRAL(R.string.formula_loop_integral, R.drawable.p_loop_integral, R.string.math_loop_integral),
        DERIVATIVE(R.string.formula_loop_derivative, R.drawable.p_loop_derivative, R.string.math_loop_derivative),
        SOLVE(R.string.formula_loop_solve, R.drawable.p_loop_solve, R.string.math_loop_solve);

        private final int shortCutId;
        private final int imageId;
        private final int descriptionId;
        private final String lowerCaseName;

        LoopType(int shortCutId, int imageId, int descriptionId)
        {
            this.shortCutId = shortCutId;
            this.imageId = imageId;
            this.descriptionId = descriptionId;
            this.lowerCaseName = name().toLowerCase(Locale.ENGLISH);
        }

        public GroupType getGroupType()
        {
            return GroupType.SERIES_INTEGRALS;
        }

        public int getShortCutId()
        {
            return shortCutId;
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
            return new SeriesIntegrals(this, termField, layout, text, textIndex);
        }
    }

    public enum CalculationStatus
    {
        NONE(ViewUtils.INVALID_INDEX),
        IS_COMPLEX(R.string.error_complex_result),
        MAX_ITERATIONS(R.string.error_maximum_iterations),
        ROOT_NOT_BRACKETED(R.string.error_root_not_bracketed);

        private final int descriptionId;

        CalculationStatus(int descriptionId)
        {
            this.descriptionId = descriptionId;
        }

        public int getDescriptionId()
        {
            return descriptionId;
        }
    }

    private static final String SYMBOL_LAYOUT_TAG = "SYMBOL_LAYOUT_TAG";
    private static final String MIN_VALUE_LAYOUT_TAG = "MIN_VALUE_LAYOUT_TAG";
    private static final String MAX_VALUE_LAYOUT_TAG = "MAX_VALUE_LAYOUT_TAG";

    /**
     * Private attributes
     */
    private TermField indexTerm = null, minValueTerm = null, maxValueTerm = null, argTerm = null;
    private LinearLayout symbolLayout = null, minValueLayout = null, maxValueLayout = null;
    private CustomTextView functionTerm = null;

    private final LoopCalculator loopCalculator = new LoopCalculator();
    private DifferentiableType differentiableType = null;

    // Attention: this is not thread-safety declaration!
    private final CalculatedValue minValue = new CalculatedValue(), maxValue = new CalculatedValue(),
            calcVal = new CalculatedValue(), argValue = new CalculatedValue();
    private CalculationStatus calcStatus = CalculationStatus.NONE;

    /*--------------------------------------------------------*
     * Constructors
     *--------------------------------------------------------*/

    private SeriesIntegrals(LoopType type, TermField owner, LinearLayout layout, String s, int idx) throws Exception
    {
        super(owner, layout);
        termType = type;
        onCreate(s, idx, owner.bracketsType);
    }

    /*--------------------------------------------------------*
     * GUI constructors to avoid lint warning
     *--------------------------------------------------------*/

    public SeriesIntegrals(Context context)
    {
        super();
    }

    public SeriesIntegrals(Context context, AttributeSet attrs)
    {
        super();
    }

    /*--------------------------------------------------------*
     * Common getters
     *--------------------------------------------------------*/

    public LoopType getLoopType()
    {
        return (LoopType) termType;
    }

    /**
     * Returns the index name for this loop
     */
    public String getIndexName()
    {
        // Do not check here ContentType of indexTerm since this procedure itself is called
        // from checkContentType for indexTerm where ContentType is not yet set
        return indexTerm.getParser().getFunctionName();
    }

    @Override
    public TermField getArgumentTerm()
    {
        return argTerm;
    }

    /*--------------------------------------------------------*
     * Re-implementation for methods for FormulaBase and FormulaTerm superclass's
     *--------------------------------------------------------*/

    @Override
    public CalculatedValue.ValueType getValue(CalculaterTask thread, CalculatedValue outValue) throws CancelException
    {
        if (termType != null)
        {
            if (!calculateBoundaries(thread))
            {
                return outValue.invalidate(CalculatedValue.ErrorType.NOT_A_REAL);
            }
            loopCalculator.setCalculaterTask(thread);
            switch (getLoopType())
            {
            case SUMMATION:
                return loopCalculator.summation(minValue.getInteger(), maxValue.getInteger(), outValue);
            case PRODUCT:
                return loopCalculator.product(minValue.getInteger(), maxValue.getInteger(), outValue);
            case INTEGRAL:
                return loopCalculator.integrate(getFormulaList().getDocumentSettings().getPrecision(), outValue);
            case DERIVATIVE:
                return loopCalculator.derivative(differentiableType, getIndexName(), outValue);
            case SOLVE:
                return loopCalculator.solve(getFormulaList().getDocumentSettings().getPrecision(), outValue);
            }
        }
        return outValue.invalidate(CalculatedValue.ErrorType.TERM_NOT_READY);
    }

    @Override
    public DifferentiableType isDifferentiable(String var)
    {
        if (isLoopDifferentiable(var))
        {
            return argTerm.isDifferentiable(var);
        }
        return DifferentiableType.NUMERICAL;
    }

    @Override
    public CalculatedValue.ValueType getDerivativeValue(String var, CalculaterTask thread, CalculatedValue outValue)
            throws CancelException
    {
        if (isLoopDifferentiable(var))
        {
            if (!calculateBoundaries(thread))
            {
                return outValue.invalidate(CalculatedValue.ErrorType.NOT_A_REAL);
            }
            loopCalculator.setCalculaterTask(thread);
            switch (getLoopType())
            {
            case SUMMATION:
                return loopCalculator.summationDerivative(var, minValue.getInteger(), maxValue.getInteger(), outValue);

            case PRODUCT:
                return loopCalculator.productDerivative(var, minValue.getInteger(), maxValue.getInteger(), outValue);

            case DERIVATIVE:
            case INTEGRAL:
                return outValue.invalidate(CalculatedValue.ErrorType.NOT_A_NUMBER);
            }
        }
        return outValue.invalidate(CalculatedValue.ErrorType.NOT_A_NUMBER);
    }

    @Override
    public boolean isContentValid(ValidationPassType type)
    {
        differentiableType = null;
        boolean isValid = true;
        switch (type)
        {
        case VALIDATE_SINGLE_FORMULA:
            isValid = super.isContentValid(type);
            final String indexName = getIndexName();
            if (isValid && termType == LoopType.DERIVATIVE)
            {
                differentiableType = argTerm.isDifferentiable(indexName);
                isValid = differentiableType != null && differentiableType != DifferentiableType.NONE;
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
        if (v.getText() != null)
        {
            String t = v.getText().toString();
            if (t.equals(getContext().getResources().getString(R.string.formula_operator_key)))
            {
                switch (getLoopType())
                {
                case SUMMATION:
                    v.prepare(CustomTextView.SymbolType.SUMMATION, getFormulaRoot().getFormulaList().getActivity(),
                            this);
                    v.setText("S..");
                    break;
                case PRODUCT:
                    v.prepare(CustomTextView.SymbolType.PRODUCT, getFormulaRoot().getFormulaList().getActivity(), this);
                    v.setText("S..");
                    break;
                case INTEGRAL:
                    v.prepare(CustomTextView.SymbolType.INTEGRAL, getFormulaRoot().getFormulaList().getActivity(), this);
                    v.setText("S..");
                    break;
                case DERIVATIVE:
                    v.prepare(CustomTextView.SymbolType.HOR_LINE, getFormulaRoot().getFormulaList().getActivity(), this);
                    break;
                case SOLVE:
                    v.prepare(CustomTextView.SymbolType.TEXT,
                            getFormulaRoot().getFormulaList().getActivity(), this);
                    v.setText(R.string.formula_loop_solve);
                    functionTerm = v;
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
            else if (t.equals(getContext().getResources().getString(R.string.formula_loop_diff)))
            {
                v.prepare(CustomTextView.SymbolType.TEXT, getFormulaRoot().getFormulaList().getActivity(), this);
            }
            else if (t.equals(getContext().getResources().getString(R.string.formula_loop_parameter_definition)) &&
                    termType == LoopType.SOLVE)
            {
                v.setText("=0");
            }
        }
        return v;
    }

    @Override
    protected CustomEditText initializeTerm(CustomEditText v, LinearLayout l)
    {
        final int addDepth = (
                termType == LoopType.INTEGRAL ||
                        termType == LoopType.DERIVATIVE ||
                        termType == LoopType.SOLVE) ? 0 : 3;
        if (v.getText() != null)
        {
            if (v.getText().toString().equals(getContext().getResources().getString(R.string.formula_max_value_key)))
            {
                maxValueTerm = addTerm(getFormulaRoot(), l, -1, v, this, addDepth);
            }
            else if (v.getText().toString()
                    .equals(getContext().getResources().getString(R.string.formula_min_value_key)))
            {
                minValueTerm = addTerm(getFormulaRoot(), l, -1, v, this, addDepth);
            }
            else if (v.getText().toString().equals(getContext().getResources().getString(R.string.formula_index_key)))
            {
                indexTerm = addTerm(getFormulaRoot(), l, -1, v, this, addDepth);
            }
            else if (v.getText().toString()
                    .equals(getContext().getResources().getString(R.string.formula_arg_term_key)))
            {
                argTerm = addTerm(getFormulaRoot(), l, v, this, false);
            }
        }
        return v;
    }

    @Override
    public void updateTextSize()
    {
        super.updateTextSize();
        final int padding = getFormulaList().getDimen().get(ScaledDimensions.Type.HOR_SYMBOL_PADDING);
        symbolLayout.setPadding(padding, 0, padding, 0);
        if (termType == LoopType.INTEGRAL && maxValueLayout != null && minValueLayout != null)
        {
            maxValueLayout.setPadding(4 * padding, 0, 0, 0);
            minValueLayout.setPadding(0, 0, 2 * padding, 0);
        }
    }

    /*--------------------------------------------------------*
     * Implementation for methods for FormulaChangeIf interface
     *--------------------------------------------------------*/
    @Override
    public void onTermSelection(View owner, boolean isSelected, ArrayList<View> list)
    {
        super.onTermSelection(owner, isSelected, list);
        if (isSelected && calcStatus != CalculationStatus.NONE &&
                calcStatus.descriptionId != ViewUtils.INVALID_INDEX)
        {
            Toast.makeText(getContext(), getContext().getResources().getString(calcStatus.descriptionId),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDelete(CustomEditText owner)
    {
        final TermField t = findTerm(owner);
        TermField r = (t != null && t != getArgumentTerm()) ? getArgumentTerm() : null;
        parentField.onTermDelete(removeElements(), r);
    }

    /*--------------------------------------------------------*
     * Implementation of ArgumentHolderIf interface
     *--------------------------------------------------------*/

    @Override
    public ArrayList<String> getArguments()
    {
        final String indexName = getIndexName();
        if (indexName != null)
        {
            ArrayList<String> retValue = new ArrayList<>();
            retValue.add(indexName);
            return retValue;
        }
        return null;
    }

    @Override
    public int getArgumentIndex(String text)
    {
        final String indexName = getIndexName();
        if (text != null && indexName != null)
        {
            return indexName.equals(text) ? 0 : ViewUtils.INVALID_INDEX;
        }
        return ViewUtils.INVALID_INDEX;
    }

    @Override
    public CalculatedValue getArgumentValue(int idx)
    {
        if (idx != 0)
        {
            argValue.invalidate(CalculatedValue.ErrorType.TERM_NOT_READY);
        }
        return argValue;
    }

    /*--------------------------------------------------------*
     * FormulaTermLoop-specific methods
     *--------------------------------------------------------*/

    /**
     * Procedure creates the formula layout
     */
    private void onCreate(String s, int idx, BracketsType bracketsType) throws Exception
    {
        switch (getLoopType())
        {
        case SUMMATION:
        case PRODUCT:
            useBrackets = bracketsType == BracketsType.ALWAYS;
            inflateElements(useBrackets ? R.layout.formula_loop_brackets : R.layout.formula_loop, true);
            break;
        case INTEGRAL:
            useBrackets = bracketsType == BracketsType.ALWAYS;
            inflateElements(useBrackets ? R.layout.formula_loop_integral_brackets : R.layout.formula_loop_integral,
                    true);
            break;
        case DERIVATIVE:
            useBrackets = bracketsType == BracketsType.ALWAYS;
            inflateElements(useBrackets ? R.layout.formula_loop_derivative_brackets : R.layout.formula_loop_derivative,
                    true);
            break;
        case SOLVE:
            useBrackets = false;
            inflateElements(R.layout.formula_loop_solve, true);
            break;
        }
        initializeElements(idx);
        symbolLayout = getLayoutWithTag(SYMBOL_LAYOUT_TAG);
        if (indexTerm == null || argTerm == null || symbolLayout == null)
        {
            throw new Exception("cannot initialize loop terms");
        }
        if (termType != LoopType.DERIVATIVE)
        {
            minValueLayout = getLayoutWithTag(MIN_VALUE_LAYOUT_TAG);
            maxValueLayout = getLayoutWithTag(MAX_VALUE_LAYOUT_TAG);
            if (minValueTerm == null || maxValueTerm == null)
            {
                throw new Exception("cannot initialize loop minimum/maximum value terms");
            }
            minValueTerm.bracketsType = BracketsType.NEVER;
            maxValueTerm.bracketsType = BracketsType.NEVER;
        }
        argTerm.bracketsType = getLoopType() == LoopType.SOLVE ?
                BracketsType.NEVER : BracketsType.IFNECESSARY;

        // restore the previous text
        final String opCode = getContext().getResources().getString(termType.getShortCutId());
        final int opPosition = s.indexOf(opCode);
        if (opPosition >= 0)
        {
            try
            {
                // the code shall be before text
                getArgumentTerm().setText(s.subSequence(opPosition + opCode.length(), s.length()));
                isContentValid(ValidationPassType.VALIDATE_SINGLE_FORMULA);
            }
            catch (Exception ex)
            {
                // nothig to do
            }
        }
    }

    private LinearLayout getLayoutWithTag(final String tag)
    {
        LinearLayout retValue = layout.findViewWithTag(tag);
        if (retValue != null)
        {
            retValue.setTag(null);
        }
        return retValue;
    }

    /**
     * Procedure checks whether summation/product loop is analytically differentiable
     */
    private boolean isLoopDifferentiable(String var)
    {
        if (termType != LoopType.SUMMATION && termType != LoopType.PRODUCT)
        {
            return false;
        }
        if (minValueTerm != null && maxValueTerm != null)
        {
            final int dGrad = Math.min(minValueTerm.isDifferentiable(var).ordinal(), maxValueTerm.isDifferentiable(var)
                    .ordinal());
            return DifferentiableType.values()[dGrad] == DifferentiableType.INDEPENDENT;
        }
        return false;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean calculateBoundaries(CalculaterTask thread) throws CancelException
    {
        minValue.invalidate(CalculatedValue.ErrorType.TERM_NOT_READY);
        if (minValueTerm != null)
        {
            minValue.processRealTerm(thread, minValueTerm);
            if (minValue.isNaN())
            {
                return false;
            }
        }

        maxValue.invalidate(CalculatedValue.ErrorType.TERM_NOT_READY);
        if (maxValueTerm != null)
        {
            maxValue.processRealTerm(thread, maxValueTerm);
            return !maxValue.isNaN();
        }
        return true;
    }

    public CustomTextView getFunctionTerm()
    {
        return functionTerm;
    }

    /**
     * Helper class that implements loop calculator
     */
    class LoopCalculator
    {
        private static final int SIMPSON_MAX_ITERATIONS_COUNT = 15;
        private static final int RIDDER_MAX_ITERATIONS_COUNT = 10;
        private static final double RIDDER_INITIAL_STEP = 0.05;
        private static final int ROOT_MAX_ITERATIONS_COUNT = 60;

        /**
         * Intermediate result.
         */
        private final CalculatedValue qtrapResult = new CalculatedValue(ValueType.REAL, 0, 0);

        /**
         * Owner calculation thread.
         */
        private CalculaterTask calculaterTask = null;

        /**
         * Class holding intermediate calculation value that can have a complex part
         */
        private class IntermediateValue
        {
            double value;
            CalculationStatus status;
            Unit<?> unit;

            IntermediateValue()
            {
                value = Double.NaN;
                status = CalculationStatus.NONE;
                unit = null;
            }

            IntermediateValue setStatus(CalculationStatus _status)
            {
                status = _status;
                return this;
            }

            IntermediateValue setReal(double v)
            {
                value = v;
                return this;
            }

            boolean isComplex()
            {
                return status == CalculationStatus.IS_COMPLEX;
            }
        }

        void setCalculaterTask(CalculaterTask calculaterTask)
        {
            this.calculaterTask = calculaterTask;
        }

        /**
         * Calculate summation
         */
        CalculatedValue.ValueType summation(long minValue, long maxValue, CalculatedValue outValue)
                throws CancelException
        {
            outValue.setValue(0.0);
            for (long idx = minValue; idx <= maxValue; idx++)
            {
                argValue.setValue((double) idx);
                argTerm.getValue(calculaterTask, calcVal);
                if (idx == minValue)
                {
                    // For the first term, use assign in oder to set units
                    outValue.assign(calcVal);
                }
                else
                {
                    outValue.add(outValue, calcVal);
                }
                if (outValue.isNaN())
                {
                    break;
                }
            }
            return outValue.getValueType();
        }

        /**
         * Calculate derivative of summation operator
         */
        CalculatedValue.ValueType summationDerivative(String var, long minValue, long maxValue,
                                                      CalculatedValue outValue) throws CancelException
        {
            outValue.setValue(0.0);
            for (long idx = minValue; idx <= maxValue; idx++)
            {
                argValue.setValue((double) idx);
                argTerm.getDerivativeValue(var, calculaterTask, calcVal);
                if (idx == minValue)
                {
                    // For the first term, use assign in oder to set units
                    outValue.assign(calcVal);
                }
                else
                {
                    outValue.add(outValue, calcVal);
                }
                if (outValue.isNaN())
                {
                    break;
                }
            }
            return outValue.getValueType();
        }

        /**
         * Calculate product
         */
        CalculatedValue.ValueType product(long minValue, long maxValue, CalculatedValue outValue)
                throws CancelException
        {
            outValue.setValue(1.0);
            for (long idx = minValue; idx <= maxValue; idx++)
            {
                argValue.setValue((double) idx);
                argTerm.getValue(calculaterTask, calcVal);
                if (idx == minValue)
                {
                    // For the first term, use assign in oder to set units
                    outValue.assign(calcVal);
                }
                else
                {
                    outValue.multiply(outValue, calcVal);
                }
                if (outValue.isNaN())
                {
                    break;
                }
            }
            return outValue.getValueType();
        }

        /**
         * Calculate derivative of product operator
         */
        CalculatedValue.ValueType productDerivative(String var, long minValue, long maxValue,
                                                    CalculatedValue outValue) throws CancelException
        {
            outValue.setValue(0.0);
            final CalculatedValue tmp1 = new CalculatedValue();
            tmp1.setValue(1.0);

            final CalculatedValue tmp2 = new CalculatedValue();
            for (long k = minValue; k <= maxValue; k++)
            {
                tmp2.setValue(1.0);
                for (long m = k + 1; m <= maxValue; m++)
                {
                    argValue.setValue((double) m);
                    argTerm.getValue(calculaterTask, calcVal);
                    tmp2.multiply(tmp2, calcVal);
                    if (tmp2.isNaN())
                    {
                        break;
                    }
                }

                argValue.setValue((double) k);
                argTerm.getDerivativeValue(var, calculaterTask, calcVal);
                calcVal.multiply(calcVal, tmp1);
                calcVal.multiply(calcVal, tmp2);
                outValue.add(outValue, calcVal);
                if (outValue.isNaN())
                {
                    break;
                }

                argTerm.getValue(calculaterTask, calcVal);
                tmp1.multiply(tmp1, calcVal);
            }
            return outValue.getValueType();
        }

        /**
         * Calculate derivative
         */
        CalculatedValue.ValueType derivative(DifferentiableType differentiableType, String indexName,
                                             CalculatedValue outValue) throws CancelException
        {
            indexTerm.getValue(calculaterTask, calcVal);
            if (!calcVal.isNaN())
            {
                if (indexName != null && differentiableType == DifferentiableType.ANALYTICAL)
                {
                    argValue.assign(calcVal);
                    return argTerm.getDerivativeValue(getIndexName(), calculaterTask, outValue);
                }
                else if (differentiableType == DifferentiableType.NUMERICAL)
                {
                    final Complex z = calcVal.getComplex();
                    final IntermediateValue re = loopCalculator.riddersDerivative(CalculatedValue.PartType.RE, z,
                            LoopCalculator.RIDDER_INITIAL_STEP);
                    if (re.isComplex())
                    {
                        final IntermediateValue im = loopCalculator.riddersDerivative(CalculatedValue.PartType.IM, z,
                                LoopCalculator.RIDDER_INITIAL_STEP);
                        return outValue.setComplexValue(re.value, im.value);
                    }
                    else
                    {
                        return outValue.setValue(re.value);
                    }
                }
            }
            return outValue.invalidate(CalculatedValue.ErrorType.NOT_A_NUMBER);
        }

        /**
         * Calculate defined integral
         */
        ValueType integrate(double absoluteAccuracy, CalculatedValue outValue) throws CancelException
        {
            if (minValue.getUnit() != null && maxValue.getUnit() != null &&
                    !minValue.getUnit().equals(maxValue.getUnit()))
            {
                return outValue.invalidate(CalculatedValue.ErrorType.INCOMPATIBLE_UNIT);
            }

            final CalculatedValue argUnit = new CalculatedValue();
            argUnit.assign(CalculatedValue.ONE);
            argUnit.setUnit(minValue.getUnit());

            final IntermediateValue re = integrateSimpsons(CalculatedValue.PartType.RE, minValue.getReal(),
                    maxValue.getReal(), absoluteAccuracy, argUnit.getUnit());
            if (Double.isNaN(re.value))
            {
                return outValue.invalidate(CalculatedValue.ErrorType.NOT_A_NUMBER);
            }

            final CalculatedValue res = new CalculatedValue();
            if (re.isComplex())
            {
                final IntermediateValue im = integrateSimpsons(CalculatedValue.PartType.IM, minValue.getReal(),
                        maxValue.getReal(), absoluteAccuracy, argUnit.getUnit());
                res.setComplexValue(re.value, im.value);
            }
            else
            {
                res.setValue(re.value);
            }
            res.setUnit(re.unit);
            return outValue.multiply(res, argUnit);
        }

        /**
         * Compute the n-th stage integral of trapezoid rule. This function should only be called by API
         * <code>integrate()</code> in the package. To save time it does not verify arguments - caller does. The
         * interval is divided equally into 2^n sections rather than an arbitrary m sections because this configuration
         * can best utilize the already computed values.
         */
        private boolean qtrapStage(CalculatedValue.PartType partType, final double min, final double max, final int n, final Unit<?> argUnit)
                throws CancelException
        {
            if (n == 0)
            {
                final CalculatedValue minVal = new CalculatedValue();
                argValue.setValue(min, argUnit);
                argTerm.getValue(calculaterTask, minVal);

                final CalculatedValue maxVal = new CalculatedValue();
                argValue.setValue(max, argUnit);
                argTerm.getValue(calculaterTask, maxVal);

                qtrapResult.setValue(0.5 * (max - min) * (minVal.getPart(partType) + maxVal.getPart(partType)));
                if (minVal.getUnit() != null && maxVal.getUnit() != null &&
                        minVal.getUnit().equals(maxVal.getUnit()))
                {
                    qtrapResult.setUnit(minVal.getUnit());
                }
                else
                {
                    qtrapResult.setUnit(null);
                }

                return (minVal.isComplex() || maxVal.isComplex());
            }
            else
            {
                boolean complexDetexted = false;
                final CalculatedValue xVal = new CalculatedValue();

                final long np = 1L << (n - 1); // number of new points in this stage
                double sum = 0;
                // spacing between adjacent new points
                final double spacing = (max - min) / np;
                double x = min + 0.5 * spacing; // the first new point
                for (long i = 0; i < np; i++)
                {
                    argValue.setValue(x);
                    argTerm.getValue(calculaterTask, xVal);
                    sum += xVal.getPart(partType);
                    x += spacing;
                    if (xVal.isComplex())
                    {
                        complexDetexted = true;
                    }
                }
                // add the new sum to previously calculated result
                final double r = 0.5 * (qtrapResult.getReal() + sum * spacing);
                qtrapResult.setValue(r);
                return complexDetexted;
            }
        }

        /**
         * Integrate the function in the given interval. Implements <a
         * href="http://mathworld.wolfram.com/SimpsonsRule.html"> Simpson's Rule</a> for integration of real univariate
         * functions. For reference, see <b>Introduction to Numerical Analysis</b>, ISBN 038795452X, chapter 3. This
         * implementation employs the basic trapezoid rule to calculate Simpson's rule.
         */
        private IntermediateValue integrateSimpsons(CalculatedValue.PartType partType, final double min,
                                                    final double max, final double absoluteAccuracy, final Unit<?> argUnit) throws CancelException
        {
            final IntermediateValue ans = new IntermediateValue();
            // Simpson's rule requires at least two trapezoid stages.
            double oldRes = 0;
            if (qtrapStage(partType, min, max, 0, argUnit))
            {
                ans.setStatus(CalculationStatus.IS_COMPLEX);
            }
            double oldt = qtrapResult.getReal();
            ans.unit = qtrapResult.getUnit();
            for (int iter = 1; iter <= SIMPSON_MAX_ITERATIONS_COUNT; iter++)
            {
                if (qtrapStage(partType, min, max, iter, argUnit))
                {
                    ans.setStatus(CalculationStatus.IS_COMPLEX);
                }
                final double t = qtrapResult.getReal();
                if (CalculatedValue.isInvalidReal(t))
                {
                    ans.value = Double.NaN;
                    ans.setStatus(CalculationStatus.NONE);
                    return ans;
                }
                final double res = (4 * t - oldt) / 3.0;
                if (iter > 1)
                {
                    final double delta = FastMath.abs(res - oldRes);
                    if (delta <= absoluteAccuracy)
                    {
                        ans.value = res;
                        return ans;
                    }
                }
                oldRes = res;
                oldt = t;
            }
            ans.value = oldRes;
            return ans;
        }

        /**
         * Returns the derivative of a function func at a point x by Ridders’ method of polynomial extrapolation. The
         * value h is input as an estimated initial stepsize; it need not be small, but rather should be an increment in
         * x over which func changes substantially. An estimate of the error in the derivative is returned as err.
         */
        @SuppressWarnings("SameParameterValue")
        IntermediateValue riddersDerivative(CalculatedValue.PartType partType, Complex z, double h)
                throws CancelException
        {
            final int NTAB = RIDDER_MAX_ITERATIONS_COUNT;
            final double CON = 1.4;
            final double CON2 = (CON * CON);

            double err = 1.0e30;
            double hh = h;
            double[][] a = new double[NTAB + 1][NTAB + 1];

            final IntermediateValue ans = new IntermediateValue();
            final CalculatedValue leftVal = new CalculatedValue();
            final CalculatedValue rightVal = new CalculatedValue();

            argValue.setComplexValue(z.getReal() + hh, z.getImaginary());
            argTerm.getValue(calculaterTask, leftVal);
            argValue.setComplexValue(z.getReal() - hh, z.getImaginary());
            argTerm.getValue(calculaterTask, rightVal);
            a[1][1] = (leftVal.getPart(partType) - rightVal.getPart(partType)) / (2.0 * hh);
            if (leftVal.isComplex() || rightVal.isComplex())
            {
                ans.setStatus(CalculationStatus.IS_COMPLEX);
            }

            for (int i = 2; i <= NTAB; i++)
            {
                hh /= CON;

                argValue.setComplexValue(z.getReal() + hh, z.getImaginary());
                argTerm.getValue(calculaterTask, leftVal);
                argValue.setComplexValue(z.getReal() - hh, z.getImaginary());
                argTerm.getValue(calculaterTask, rightVal);
                a[1][i] = (leftVal.getPart(partType) - rightVal.getPart(partType)) / (2.0 * hh);
                if (leftVal.isComplex() || rightVal.isComplex())
                {
                    ans.setStatus(CalculationStatus.IS_COMPLEX);
                }

                double fac = CON2;
                for (int j = 2; j <= i; j++)
                {
                    a[j][i] = (a[j - 1][i] * fac - a[j - 1][i - 1]) / (fac - 1.0);
                    fac = CON2 * fac;
                    final double errt = FastMath.max(FastMath.abs(a[j][i] - a[j - 1][i]),
                            FastMath.abs(a[j][i] - a[j - 1][i - 1]));
                    if (errt <= err)
                    {
                        err = errt;
                        ans.value = a[j][i];
                    }
                }
                if (FastMath.abs(a[i][i] - a[i - 1][i - 1]) >= 2.0 * (err))
                {
                    break;
                }
            }
            return ans;
        }

        public ValueType solve(double absoluteAccuracy, CalculatedValue outValue) throws CancelException
        {
            if (minValue.isComplex() || maxValue.isComplex())
            {
                calcStatus = CalculationStatus.IS_COMPLEX;
                return outValue.invalidate(CalculatedValue.ErrorType.PASSED_COMPLEX);
            }
            final IntermediateValue val = riddersRoot(minValue.getReal(), maxValue.getReal(), absoluteAccuracy);
            calcStatus = val.status;
            switch (val.status)
            {
            case IS_COMPLEX:
            case MAX_ITERATIONS:
            case ROOT_NOT_BRACKETED:
                ViewUtils.Debug(this, "Can not solve, error = " + val.status);
                return outValue.invalidate(CalculatedValue.ErrorType.NOT_A_REAL);
            default:
                return outValue.setValue(val.value);
            }
        }

        /**
         * Ridders’ Method from Numerical Recipes
         * Using Ridders’ method, return the root of a function func known to lie between x1 and x2.
         * The root, returned in this method, will be refined to an approximate accuracy xacc.
         */
        private IntermediateValue riddersRoot(double x1, double x2, double xacc) throws CancelException
        {
            final IntermediateValue retValue = new IntermediateValue();
            final CalculatedValue fVal = new CalculatedValue();
            final double UNUSED = -1.11e30;
            double ans, fh, fl, fm, fnew, s, xh, xl, xm, xnew;

            //fl=(*func)(x1);
            argValue.setValue(x1);
            argTerm.getValue(calculaterTask, fVal);
            if (fVal.isComplex())
            {
                return retValue.setStatus(CalculationStatus.IS_COMPLEX);
            }
            fl = fVal.getReal();

            //fh=(*func)(x2);
            argValue.setValue(x2);
            argTerm.getValue(calculaterTask, fVal);
            if (fVal.isComplex())
            {
                return retValue.setStatus(CalculationStatus.IS_COMPLEX);
            }
            fh = fVal.getReal();

            if ((fl > 0.0 && fh < 0.0) || (fl < 0.0 && fh > 0.0))
            {
                xl = x1;
                xh = x2;
                ans = UNUSED;
                for (int j = 1; j <= ROOT_MAX_ITERATIONS_COUNT; j++)
                {
                    xm = 0.5 * (xl + xh);

                    //fm=(*func)(xm);
                    argValue.setValue(xm);
                    argTerm.getValue(calculaterTask, fVal);
                    if (fVal.isComplex())
                    {
                        return retValue.setStatus(CalculationStatus.IS_COMPLEX);
                    }
                    fm = fVal.getReal();

                    s = FastMath.sqrt(fm * fm - fl * fh);
                    if (s == 0.0) return retValue.setReal(ans);
                    xnew = xm + (xm - xl) * ((fl >= fh ? 1.0 : -1.0) * fm / s);
                    if (FastMath.abs(xnew - ans) <= xacc) return retValue.setReal(ans);
                    ans = xnew;

                    //fnew=(*func)(ans);
                    argValue.setValue(ans);
                    argTerm.getValue(calculaterTask, fVal);
                    if (fVal.isComplex())
                    {
                        return retValue.setStatus(CalculationStatus.IS_COMPLEX);
                    }
                    fnew = fVal.getReal();

                    if (fnew == 0.0) return retValue.setReal(ans);
                    if (SIGN(fm, fnew) != fm)
                    {
                        xl = xm;
                        fl = fm;
                        xh = ans;
                        fh = fnew;
                    }
                    else if (SIGN(fl, fnew) != fl)
                    {
                        xh = ans;
                        fh = fnew;
                    }
                    else if (SIGN(fh, fnew) != fh)
                    {
                        xl = ans;
                        fl = fnew;
                    }
                    if (FastMath.abs(xh - xl) <= xacc) return retValue.setReal(ans);
                }
                return retValue.setStatus(CalculationStatus.MAX_ITERATIONS);
            }
            else
            {
                if (fl == 0.0) return retValue.setReal(x1);
                if (fh == 0.0) return retValue.setReal(x2);
                return retValue.setStatus(CalculationStatus.ROOT_NOT_BRACKETED);
            }
        }

        private double SIGN(double a, double b)
        {
            return ((b) >= 0.0 ? FastMath.abs(a) : -FastMath.abs(a));
        }
    }
}
