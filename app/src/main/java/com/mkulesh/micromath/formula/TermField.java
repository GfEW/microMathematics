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
package com.mkulesh.micromath.formula;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.mkulesh.micromath.formula.CalculaterTask.CancelException;
import com.mkulesh.micromath.formula.FormulaBase.FocusType;
import com.mkulesh.micromath.formula.PaletteButton.Category;
import com.mkulesh.micromath.formula.terms.ArrayFunctions;
import com.mkulesh.micromath.formula.terms.Comparators;
import com.mkulesh.micromath.formula.terms.Intervals;
import com.mkulesh.micromath.formula.terms.TermFactory;
import com.mkulesh.micromath.formula.terms.TermTypeIf;
import com.mkulesh.micromath.formula.terms.UserFunctions;
import com.mkulesh.micromath.io.XmlUtils;
import com.mkulesh.micromath.math.CalculatedValue;
import com.mkulesh.micromath.plus.R;
import com.mkulesh.micromath.properties.MatrixProperties;
import com.mkulesh.micromath.undo.FormulaState;
import com.mkulesh.micromath.utils.CompatUtils;
import com.mkulesh.micromath.utils.IdGenerator;
import com.mkulesh.micromath.utils.ViewUtils;
import com.mkulesh.micromath.widgets.CustomEditText;
import com.mkulesh.micromath.widgets.CustomLayout;
import com.mkulesh.micromath.widgets.FocusChangeIf;
import com.mkulesh.micromath.widgets.ScaledDimensions;
import com.mkulesh.micromath.widgets.TextChangeIf;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.util.ArrayList;
import java.util.Locale;

public class TermField implements TextChangeIf, FocusChangeIf, CalculatableIf
{
    /*
     * Constants used to save/restore the instance state.
     */
    private static final String STATE_TERM_ID = "_term_id";
    private static final String STATE_TEXT = "_text";
    private static final String STATE_CODE = "_code";
    private static final String STATE_INSTANCE = "_instance";
    public static final String STATE_DIMENSION = "_dimension";

    private int MAX_LAYOUT_DEPTH = 15;
    public static final int NO_ERROR_ID = -1;

    private final FormulaBase formulaRoot, parentFormula;
    private final LinearLayout layout;
    private final CustomEditText text;
    private FormulaTerm term = null;
    private String termKey = null;
    public boolean isWritable = true;
    private boolean emptyOrAutoContent = true; // empty or automatically filled content
    private boolean textChangeDetectionEnabled = true;
    private boolean pasteFromClipboard = false;

    // content type and content parser
    public enum ContentType
    {
        INVALID,
        EMPTY,
        INFO_TEXT,
        TERM,
        EQUATION_NAME,
        NUMBER,
        ARGUMENT,
        VARIABLE_LINK
    }

    public enum BracketsType
    {
        NEVER,
        IFNECESSARY,
        ALWAYS
    }

    private ContentType contentType = ContentType.INVALID;
    private final TermParser parser = new TermParser();
    private Equation linkedVariable = null;

    // custom errors that can be set externally
    public enum ErrorNotification
    {
        COLOR,
        LAYOUT_BORDER,
        PARENT_LAYOUT
    }

    private String errorMsg = null;
    private ErrorNotification errorNotification = ErrorNotification.COLOR;
    private int errorId = NO_ERROR_ID;

    // public flags
    public BracketsType bracketsType = BracketsType.ALWAYS;
    public int termDepth = 0;

    /*--------------------------------------------------------*
     * Constructors
     *--------------------------------------------------------*/

    public TermField(FormulaBase formulaRoot, FormulaBase parentFormula, LinearLayout layout, int termDepth,
                     CustomEditText text)
    {
        super();

        MAX_LAYOUT_DEPTH = 25;
        if (Build.VERSION.SDK_INT < 23)
        {
            // This maximal layout depth was obtain for Lenovo P780
            MAX_LAYOUT_DEPTH = 15;
        }
        if (Build.VERSION.SDK_INT < 17)
        {
            // This maximal layout depth was obtain for Motorolla Xoom and Xtreamer Aiki
            MAX_LAYOUT_DEPTH = 9;
        }
        if (Build.VERSION.SDK_INT < 15)
        {
            // This maximal layout depth was obtain on Alcatel OT 911
            MAX_LAYOUT_DEPTH = 6;
        }

        this.formulaRoot = formulaRoot;
        this.parentFormula = parentFormula;
        this.layout = layout;
        this.termDepth = termDepth;
        termKey = text.getText().toString();
        this.text = text;
        this.text.setText("");
        this.text.setChangeIf(this, this);
        this.text.setId(IdGenerator.generateId());
        updateViewColor();
    }

    /*--------------------------------------------------------*
     * Methods used recursively for the formula tree
     *--------------------------------------------------------*/

    public void collectElemets(LinearLayout layout, ArrayList<View> out)
    {
        if (isTerm() && layout == this.layout)
        {
            term.collectElemets(layout, out);
        }
    }

    /**
     * Procedure checks term field content and sets the corresponding content type
     */
    public ContentType checkContentType()
    {
        return checkContentType(true);
    }

    public ContentType checkContentType(boolean registerLinkedEquation)
    {
        errorMsg = null;
        errorNotification = ErrorNotification.COLOR;
        errorId = NO_ERROR_ID;
        linkedVariable = null;
        if (text.isTextFragment() || text.isCalculatedValue() || text.isFileName())
        {
            contentType = ContentType.INFO_TEXT;
            updateViewColor();
            return contentType;
        }
        if (text.isEmptyEnabled() && isEmpty())
        {
            contentType = ContentType.EMPTY;
            updateViewColor();
            return contentType;
        }
        if (isTerm())
        {
            contentType = (term.isContentValid(FormulaBase.ValidationPassType.VALIDATE_SINGLE_FORMULA)) ? ContentType.TERM
                    : ContentType.INVALID;
            updateViewColor();
            return contentType;
        }
        contentType = ContentType.INVALID;
        parser.setText(this, formulaRoot, text);
        if (isEquationName())
        {
            // in this mode, only a name is allowed and shall be unique
            if (parser.getFunctionName() != null && parser.errorId == NO_ERROR_ID)
            {
                contentType = ContentType.EQUATION_NAME;
            }
            if (getIndexTerm() != null)
            {
                contentType = ContentType.EQUATION_NAME;
            }
        }
        else
        {
            // in this mode, numbers and function pointers are allowed
            if (parser.getValue() != null)
            {
                contentType = ContentType.NUMBER;
            }
            else if (parser.getArgumentHolder() != null && parser.getArgumentIndex() != ViewUtils.INVALID_INDEX)
            {
                contentType = ContentType.ARGUMENT;
            }
            else if (parser.getLinkedVariableId() >= 0)
            {
                final FormulaBase lv = formulaRoot.getFormulaList().getFormula(parser.getLinkedVariableId());
                if (lv instanceof Equation)
                {
                    contentType = ContentType.VARIABLE_LINK;
                    linkedVariable = (Equation) lv;
                    if (registerLinkedEquation && formulaRoot instanceof LinkHolder)
                    {
                        ((LinkHolder) formulaRoot).addLinkedEquation(linkedVariable);
                    }
                }
            }
        }
        updateViewColor();
        return contentType;
    }

    /**
     * Procedure returns true if the calculation and content checking shall be skipped for this formula
     */
    private boolean disableCalculation()
    {
        return (getFormulaRoot() instanceof CalculationResult)
                && ((CalculationResult) getFormulaRoot()).disableCalculation();
    }

    /**
     * Procedure calculates recursively the formula value
     */
    public CalculatedValue.ValueType getValue(CalculaterTask thread, CalculatedValue outValue) throws CancelException
    {
        if (thread != null)
        {
            thread.checkCancelation();
        }
        if (isTerm())
        {
            return term.getValue(thread, outValue);
        }
        else
        {
            switch (contentType)
            {
            case NUMBER:
                outValue.assign(parser.getValue());
                if (parser.getUnit() != null)
                {
                    outValue.convertUnit(parser.getUnit(), parser.getUnit().getStandardUnit());
                }
                return outValue.getValueType();
            case ARGUMENT:
                outValue.assign(parser.getArgumentHolder().getArgumentValue(parser.getArgumentIndex()));
                if (parser.getUnit() != null)
                {
                    outValue.convertUnit(parser.getUnit(), parser.getUnit().getStandardUnit());
                }
                return outValue.multiply(parser.getSign());
            case VARIABLE_LINK:
                if (linkedVariable.isInterval())
                {
                    outValue.assign(linkedVariable.getArgumentValue(0));
                }
                else
                {
                    linkedVariable.getValue(thread, outValue);
                }
                if (parser.getUnit() != null)
                {
                    outValue.convertUnit(
                            (outValue.getUnit() != null) ? outValue.getUnit() : parser.getUnit(),
                            parser.getUnit().getStandardUnit());
                }
                return outValue.multiply(parser.getSign());
            default:
                return outValue.invalidate(CalculatedValue.ErrorType.TERM_NOT_READY);
            }
        }
    }

    /**
     * Procedure checks whether this term holds a differentiable equation with respect to given variable name
     */
    public DifferentiableType isDifferentiable(String var)
    {
        if (isTerm())
        {
            return term.isDifferentiable(var);
        }
        if (contentType == ContentType.ARGUMENT && parser.isArgumentInHolder(var))
        {
            return DifferentiableType.ANALYTICAL;
        }
        return DifferentiableType.INDEPENDENT;
    }

    /**
     * Procedure calculates recursively the derivative value
     */
    public CalculatedValue.ValueType getDerivativeValue(String var, CalculaterTask thread, CalculatedValue outValue)
            throws CancelException
    {
        if (isTerm())
        {
            return term.getDerivativeValue(var, thread, outValue);
        }
        if (contentType == ContentType.ARGUMENT && parser.isArgumentInHolder(var))
        {
            return outValue.setValue(parser.getSign());
        }
        return outValue.setValue(0.0, null /* erase units */);
    }

    /**
     * Procedure searches the focused term recursively
     */
    public TermField findFocusedTerm()
    {
        if (isTerm())
        {
            if (term == formulaRoot.getFormulaList().getSelectedTerm())
            {
                return this;
            }
            return term.findFocusedTerm();
        }
        return (text.isFocused() ? this : null);
    }

    /**
     * Procedure sets the focused term recursively
     */
    public boolean setEditableFocus(FocusType type)
    {
        if (isTerm())
        {
            return term.setEditableFocus(type);
        }
        else if (type == FormulaBase.FocusType.FIRST_EDITABLE || isEmpty())
        {
            text.requestFocus();
            formulaRoot.getFormulaList().showSoftKeyboard(true, text);
            return true;
        }
        return false;
    }

    /**
     * Procedure updates the text size of this term depending on layout depth
     */
    public void updateTextSize()
    {
        if (isTerm())
        {
            term.updateTextSize();
        }
        else
        {
            text.updateTextSize(formulaRoot.getFormulaList().getDimen(), termDepth,
                    ScaledDimensions.Type.HOR_TEXT_PADDING);
        }
    }

    /**
     * Procedure updates the text color of this term depending on layout depth
     */
    public void updateTextColor()
    {
        if (isTerm())
        {
            term.updateTextColor();
        }
        else
        {
            updateViewColor();
        }
    }

    /*--------------------------------------------------------*
     * Implementation of TextChangeIf interface
     *--------------------------------------------------------*/

    @Override
    public void beforeTextChanged(boolean isManualInput)
    {
        if (isManualInput)
        {
            if (text.isNewTermEnabled())
            {
                formulaRoot.getFormulaList().getUndoState().addEntry(parentFormula.getState());
            }
            else
            {
                formulaRoot.getFormulaList().getUndoState().addEntry(getState());
            }
        }
    }

    @Override
    public void onTextChanged(String s, boolean isManualInput)
    {
        boolean converted = false;
        final boolean isEmpty = (s == null || s.length() == 0);
        if (textChangeDetectionEnabled)
        {
            emptyOrAutoContent = isEmpty;
        }
        boolean conversionEnabled = !isEmpty && text.isConversionEnabled();
        if (!isEmpty && isEquationName() && s.contains(getContext().getString(R.string.formula_function_start_index)))
        {
            conversionEnabled = true;
        }
        if (conversionEnabled)
        {
            term = convertToTerm(s, null, /*ensureManualTrigger=*/ true);
            if (term != null)
            {
                converted = true;
                if (isManualInput)
                {
                    requestFocus();
                }
            }
        }
        if (!isEmpty && !converted && text.isNewTermEnabled())
        {
            if (parentFormula.onNewTerm(this, s, true))
            {
                return;
            }
        }
        if (!isTerm())
        {
            // Do not call isContentValid since it deletes content of edit text
            // that causes unlimited recursive call of onTextChanged
            checkContentType();
        }
    }

    @Override
    public void onSizeChanged()
    {
        if (!isTerm() && text.isFocused())
        {
            formulaRoot.getFormulaList().getFormulaScrollView().scrollToChild(text);
        }
    }

    @Override
    public int onGetNextFocusId(CustomEditText owner, FocusChangeIf.NextFocusType focusType)
    {
        return parentFormula.getNextFocusId(text, focusType);
    }

    /*--------------------------------------------------------*
     * Read/write interface
     *--------------------------------------------------------*/

    /**
     * Parcelable interface: procedure writes the formula state
     */
    public void writeToBundle(Bundle bundle, String pref)
    {
        bundle.putInt(pref + STATE_TERM_ID, getTermId());
        if (!isTerm())
        {
            bundle.putString(pref + STATE_TEXT, isEmptyOrAutoContent() ? "" : getText());
            bundle.putString(pref + STATE_CODE, "");
        }
        else
        {
            bundle.putString(pref + STATE_TEXT, "");
            bundle.putString(pref + STATE_CODE, term.getTermCode());
            bundle.putParcelable(pref + STATE_INSTANCE, term.onSaveInstanceState());
            if (isArrayTerm(term.getTermCode()))
            {
                bundle.putParcelable(pref + STATE_DIMENSION, getArrayDimension());
            }
        }
    }

    /**
     * Parcelable interface: procedure reads the formula state
     */
    public void readFromBundle(Bundle bundle, String pref)
    {
        if (IdGenerator.enableIdRestore)
        {
            text.setId(bundle.getInt(pref + STATE_TERM_ID));
            IdGenerator.compareAndSet(getTermId());
        }
        setText(bundle.getString(pref + STATE_TEXT));
        final String termCode = bundle.getString(pref + STATE_CODE);
        if (termCode != null && termCode.length() > 0)
        {
            final Parcelable p = bundle.getParcelable(pref + STATE_INSTANCE);
            Object extraPar = null;
            if (isArrayTerm(termCode))
            {
                final MatrixProperties dim = bundle.getParcelable(pref + STATE_DIMENSION);
                extraPar = dim;
            }
            term = convertToTerm(termCode, p, /*ensureManualTrigger=*/ false, extraPar);
        }
    }

    /**
     * XML interface: procedure reads the formula state
     */
    public void readFromXml(XmlPullParser parser) throws Exception
    {
        final String text = parser.getAttributeValue(null, FormulaList.XML_PROP_TEXT);
        final String unit = parser.getAttributeValue(null, FormulaList.XML_PROP_UNIT);
        final String termCode = parser.getAttributeValue(null, FormulaList.XML_PROP_CODE);
        parser.require(XmlPullParser.START_TAG, FormulaList.XML_NS, parser.getName());
        boolean finishTag = true;
        if (termCode == null)
        {
            if (text == null || !isWritable)
            {
                setText("");
            }
            else if (unit != null && !unit.isEmpty())
            {
                setText(text.isEmpty() ? unit : text + TermParser.UNIT_SEPARATOR + unit);
            }
            else
            {
                setText(text);
            }
        }
        else
        {
            // read extra parameters for some terms
            Object extraPar = null;
            if (isArrayTerm(termCode))
            {
                final MatrixProperties dim = new MatrixProperties();
                dim.readFromXml(parser);
                extraPar = dim;
            }
            term = convertToTerm(termCode, null, /*ensureManualTrigger=*/ false, extraPar);
            setText("");
            if (isTerm())
            {
                term.readFromXml(parser);
                finishTag = false;
            }
            else
            {
                ViewUtils.Debug(this, "Term code \"" + termCode + "\" is not known");
                ViewUtils.Debug(this, "Skipping tag: " + parser.getPositionDescription());
                XmlUtils.skipTagContent(parser);
                finishTag = false;
            }
        }
        if (finishTag)
        {
            while (parser.next() != XmlPullParser.END_TAG) ;
        }
    }

    /**
     * XML interface: procedure returns string that contains XML representation of this term
     */
    public void writeToXml(XmlSerializer serializer) throws Exception
    {
        if (!isTerm())
        {
            if (isEmptyOrAutoContent())
            {
                serializer.attribute(FormulaList.XML_NS, FormulaList.XML_PROP_TEXT, "");
            }
            else if (parser.getUnit() != null && parser.getUnitTags() != null)
            {
                serializer.attribute(FormulaList.XML_NS,
                        FormulaList.XML_PROP_TEXT, parser.getUnitTags().getFirst());
                serializer.attribute(FormulaList.XML_NS,
                        FormulaList.XML_PROP_UNIT, parser.getUnitTags().getSecond());
            }
            else
            {
                serializer.attribute(FormulaList.XML_NS, FormulaList.XML_PROP_TEXT, getText());
            }
        }
        else
        {
            serializer.attribute(FormulaList.XML_NS, FormulaList.XML_PROP_CODE, term.getTermCode());
            final MatrixProperties dim = getArrayDimension();
            if (isArrayTerm(term.getTermCode()) && dim != null)
            {
                dim.writeToXml(serializer);
            }
            term.writeToXml(serializer, getTermKey());
        }
    }

    /*--------------------------------------------------------*
     * Undo feature
     *--------------------------------------------------------*/

    /**
     * Procedure stores undo state for this term
     */
    public FormulaState getState()
    {
        Bundle bundle = new Bundle();
        writeToBundle(bundle, "");
        return new FormulaState(formulaRoot.getId(), getTermId(), bundle);
    }

    public void undo(FormulaState state)
    {
        if (state.data instanceof Bundle)
        {
            clear();
            readFromBundle((Bundle) state.data, "");
        }
    }

    /*--------------------------------------------------------*
     * TermField-specific methods
     *--------------------------------------------------------*/

    /**
     * Procedure returns the context for this term field
     */
    private Context getContext()
    {
        return formulaRoot.getFormulaList().getContext();
    }

    /**
     * Procedure returns the parent layout
     */
    public LinearLayout getLayout()
    {
        return layout;
    }

    /**
     * Procedure returns root formula for this term
     */
    public FormulaBase getFormulaRoot()
    {
        return formulaRoot;
    }

    /**
     * Procedure returns parent formula
     */
    public FormulaBase getParentFormula()
    {
        return parentFormula;
    }

    /**
     * Procedure sets given text for this term field
     */
    public void setText(CharSequence s)
    {
        if (textChangeDetectionEnabled)
        {
            // this check duplicates the same check in the onTextChanged since onTextChanged is not always called
            emptyOrAutoContent = (s == null || s.length() == 0);
        }
        text.setTextWatcherActive(false);
        if (s == null)
        {
            s = "";
        }
        text.setText(s);
        onTextChanged(s.toString(), false);
        text.setTextWatcherActive(true);
    }

    public void pasteFromClipboard(CharSequence s)
    {
        pasteFromClipboard = true;
        setText(s);
        pasteFromClipboard = false;
    }

    public boolean isPasteFromClipboard()
    {
        return pasteFromClipboard;
    }

    /**
     * Procedure actual text for this term field
     */
    public String getText()
    {
        return text.getText().toString();
    }

    /**
     * Procedure returns associated edit text component
     */
    public CustomEditText getEditText()
    {
        return text;
    }

    /**
     * Procedure checks whether there is a valid term
     */
    public boolean isTerm()
    {
        return term != null;
    }

    /**
     * Procedure checks whether this term is empty
     */
    public boolean isEmpty()
    {
        return !isTerm() && text.getText().length() == 0;
    }

    /**
     * Procedure returns the associated term
     */
    public FormulaTerm getTerm()
    {
        return term;
    }

    /**
     * Procedure returns the term id
     */
    public int getTermId()
    {
        return text.getId();
    }

    /**
     * Procedure returns the unique key of this term
     */
    public String getTermKey()
    {
        return termKey;
    }

    /**
     * Procedure sets the unique key of this term
     */
    public void setTermKey(String termKey)
    {
        this.termKey = termKey;
    }

    /**
     * Procedure checks whether this term is an editable term and holds unit definition
     */
    public boolean isInputUnit()
    {
        return !isTerm() && parser.getUnit() != null;
    }

    /**
     * Procedure returns linked equation if it links to an array
     */
    public Equation getLinkedArray()
    {
        if (contentType == ContentType.VARIABLE_LINK && linkedVariable != null && (linkedVariable.isArray() || linkedVariable.isInterval()))
        {
            return linkedVariable;
        }
        return null;
    }

    /**
     * Procedure returns associated parser
     */
    public TermParser getParser()
    {
        return parser;
    }

    /**
     * Procedure returns whether this field empty or contains automatically filled content
     */
    public boolean isEmptyOrAutoContent()
    {
        return !isTerm() && (isEmpty() || emptyOrAutoContent);
    }

    public void setTextChangeDetectionEnabled(boolean textChangeDetectionEnabled)
    {
        this.textChangeDetectionEnabled = textChangeDetectionEnabled;
    }

    /**
     * Procedure updates the border and color of edit text depends on its content
     */
    private void updateViewColor()
    {
        // flag whether an error is detected: for a formula that will be not calculated,
        // errors will be not shown
        final boolean parentTermError = !disableCalculation() && (errorId != NO_ERROR_ID || errorMsg != null);
        final boolean thisTermError = !disableCalculation() && contentType == ContentType.INVALID
                && errorNotification == ErrorNotification.COLOR;

        // layout border
        if (layout instanceof CustomLayout)
        {
            ((CustomLayout) layout).setContentValid(true);
            if (isTerm() && errorNotification == ErrorNotification.LAYOUT_BORDER && parentTermError)
            {
                ((CustomLayout) layout).setContentValid(false);
                return;
            }
        }

        // text border
        if (text.isSelected())
        {
            text.setBackgroundAttr(R.drawable.formula_term_background, R.attr.colorFormulaSelected);
        }
        else if (parentTermError || thisTermError)
        {
            text.setBackgroundAttr(R.drawable.formula_term_border, R.attr.colorFormulaInvalid);
        }
        else if (isEmpty())
        {
            final int attrId = (text.isEmptyEnabled()) ?
                    R.attr.colorFormulaEmpty : R.attr.colorFormulaInvalid;
            text.setBackgroundAttr(R.drawable.formula_term_border, attrId);
        }
        else
        {
            text.setBackgroundAttr(R.drawable.formula_term, Integer.MIN_VALUE);
        }

        // text color
        {
            int resId = R.attr.colorFormulaNormal;
            if (text.isCalculatedValue() || (!isEmpty() && isEmptyOrAutoContent()))
            {
                resId = R.attr.colorFormulaCalculatedValue;
            }
            else if (text.isTextFragment())
            {
                resId = R.attr.colorFormulaTextFragment;
            }
            final int color = CompatUtils.getThemeColorAttr(getContext(), resId);
            if (color != text.getCurrentTextColor())
            {
                text.setTextColor(color);
            }
        }

        // update minimum width depending on content
        text.updateMinimumWidth(formulaRoot.getFormulaList().getDimen());
    }

    /**
     * Procedure check that the current formula depth has no conflicts with allowed formula depth
     */
    public boolean checkFormulaDepth()
    {
        repairTermDepth(false);
        final int layoutDepth = ViewUtils.getLayoutDepth(layout);
        return (layoutDepth <= MAX_LAYOUT_DEPTH);
    }

    /**
     * Procedure check that the term has no conflicts with allowed formula depth
     */
    private void repairTermDepth(boolean showToast)
    {
        if (isTerm() && !term.checkFormulaDepth())
        {
            clear();
            if (showToast)
            {
                Toast.makeText(getContext(), getContext().getResources().getString(R.string.error_max_layout_depth),
                        Toast.LENGTH_SHORT).show();
            }
            else
            {
                formulaRoot.getFormulaList().getFormulaListView().setTermDeleted(true);
            }
        }
    }

    /**
     * Procedure converts this term field to an other term
     */
    private FormulaTerm convertToTerm(String s, Parcelable p, boolean ensureManualTrigger)
    {
        return convertToTerm(s, p, ensureManualTrigger, null);
    }

    private FormulaTerm convertToTerm(String s, Parcelable p, boolean ensureManualTrigger, Object extraPar)
    {
        final TermTypeIf f = TermFactory.findTerm(getContext(), text, s, ensureManualTrigger, true);
        return convertToTermType(f, s, p, extraPar);
    }

    private FormulaTerm convertToTermType(final TermTypeIf f, String s, Parcelable p, Object extraPar)
    {
        term = null;
        if (f != null)
        {
            try
            {
                final int textIndex = ViewUtils.getViewIndex(layout, text); // store view index before it will be removed
                text.setTextWatcher(false);
                if (text.isFocused())
                {
                    formulaRoot.getFormulaList().clearFocus();
                }
                layout.removeView(text);
                if (textIndex < 0 || textIndex > layout.getChildCount())
                {
                    throw new Exception("cannot create " + f.toString() + " for invalid insertion index " + textIndex);
                }
                term = f.createTerm(this, layout, s, textIndex, extraPar);
                term.updateTextSize();
            }
            catch (Exception ex)
            {
                ViewUtils.Debug(this, ex.getLocalizedMessage());
                layout.addView(text);
                text.setTextWatcher(true);
            }
        }
        repairTermDepth(true);
        if (isTerm())
        {
            setText("");
            if (p != null)
            {
                term.onRestoreInstanceState(p);
            }
        }
        return term;
    }

    /**
     * Procedure adds the given operator code to this term
     */
    public void addOperatorCode(String code, final Object extPar)
    {
        if (FormulaBase.BaseType.TERM.toString().equals(code.toUpperCase(Locale.ENGLISH)))
        {
            code = getContext().getResources().getString(R.string.formula_term_separator);
        }
        if (text.isNewTermEnabled())
        {
            formulaRoot.getFormulaList().getUndoState().addEntry(parentFormula.getState());
            if (parentFormula.onNewTerm(this, code, true))
            {
                return;
            }
        }

        final TermTypeIf termType = TermFactory.findTerm(
                getContext(), null, code, false, true);
        if (termType == null)
        {
            return;
        }

        formulaRoot.getFormulaList().getUndoState().addEntry(getState());

        // comparator change
        if (isTerm() && getTerm() instanceof Comparators && termType instanceof Comparators.ComparatorType)
        {
            final Comparators.ComparatorType t2 = (Comparators.ComparatorType) termType;
            if (((Comparators) getTerm()).changeComparatorType(t2))
            {
                return;
            }
        }

        final String prevText = getText();
        String newText = prevText;
        if (termType instanceof UserFunctions.FunctionType)
        {
            final UserFunctions.FunctionType t1 = (UserFunctions.FunctionType) termType;
            newText = (t1 == UserFunctions.FunctionType.FUNCTION_LINK) ? code : t1.getLowerCaseName();
            if (prevText != null)
            {
                if (t1 != UserFunctions.FunctionType.FUNCTION_LINK)
                {
                    newText += getContext().getResources().getString(R.string.formula_function_start_bracket);
                }
                newText += prevText;
            }
        }

        text.setRequestFocusEnabled(false);
        Bundle savedState = null;
        if (isTerm())
        {
            savedState = new Bundle();
            writeToBundle(savedState, "savedState");
            clear();
        }
        convertToTermType(termType, newText, null, extPar);

        if (isTerm() && !term.getTerms().isEmpty() && savedState != null)
        {
            final TermField tf = term.getInsertionRefTerm();
            if (tf != null && tf.getEditText() != null && tf.getEditText().isConversionEnabled())
            {
                tf.readFromBundle(savedState, "savedState");
            }
        }
        repairTermDepth(true);
        text.setRequestFocusEnabled(true);
        requestFocus();
    }

    /**
     * Procedure reads this term field from the given bundle
     */
    public void readStoredFormula(StoredFormula s, boolean showError)
    {
        if (text.isConversionEnabled())
        {
            final String termCode = s.getSingleData().termCode;
            final Parcelable p = s.getSingleData().data;
            Object extraPar = null;
            if (isArrayTerm(termCode) && p instanceof Bundle)
            {
                final MatrixProperties dim = ((Bundle) p).getParcelable(STATE_DIMENSION);
                extraPar = dim;
            }
            term = convertToTerm(termCode, p, /*ensureManualTrigger=*/ false, extraPar);
        }
        else if (showError)
        {
            final String error = formulaRoot.getFormulaList().getActivity().getResources()
                    .getString(R.string.error_paste_term_into_text);
            Toast.makeText(formulaRoot.getFormulaList().getActivity(), error, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Callback used to indicate that a child term terminates itself
     */
    public void onTermDelete(int idx, TermField remainingTerm)
    {
        formulaRoot.getFormulaList().getUndoState().addEntry(getState());
        deleteTerm(idx, remainingTerm, "");
    }

    public void onTermDeleteWithText(int idx, CharSequence newText)
    {
        formulaRoot.getFormulaList().getUndoState().addEntry(getState());
        deleteTerm(idx, null, newText);
    }

    /**
     * Procedure deletes the term and restores the edit text field
     */
    private void deleteTerm(int idx, TermField remainingTerm, CharSequence newText)
    {
        if (!isTerm())
        {
            // onTermDelete can be only called from the valid term
            return;
        }
        term = null;
        layout.addView(text, idx);
        text.setSelected(false);
        if (remainingTerm != null)
        {
            setText(remainingTerm.getText());
            if (remainingTerm.term != null)
            {
                Parcelable p = remainingTerm.term.onSaveInstanceState();
                term = convertToTerm(remainingTerm.term.getTermCode(), p, /*ensureManualTrigger=*/ false);
            }
        }
        else
        {
            setText(newText);
        }
        if (!isTerm())
        {
            text.setTextWatcher(true);
            updateTextSize();
        }
        checkContentType();
        requestFocus();
    }

    /**
     * Procedure deletes content of this term field
     */
    public void clear()
    {
        boolean flag = text.isRequestFocusEnabled();
        text.setRequestFocusEnabled(false);
        if (isTerm())
        {
            deleteTerm(term.removeElements(), null, "");
        }
        else
        {
            setText("");
        }
        text.setRequestFocusEnabled(flag);
    }

    /**
     * If there is a parsing error, it will be shown
     */
    public void showParsingError()
    {
        final String errMsg = findErrorMsg();
        if (errMsg != null)
        {
            Toast.makeText(formulaRoot.getContext(), errMsg, Toast.LENGTH_SHORT).show();
        }
        else if (errorId != NO_ERROR_ID)
        {
            Toast.makeText(formulaRoot.getContext(), formulaRoot.getContext().getResources().getString(errorId),
                    Toast.LENGTH_SHORT).show();
        }
        else if (!isEmpty() && contentType == ContentType.INVALID && parser.errorId != NO_ERROR_ID)
        {
            Toast.makeText(formulaRoot.getContext(), formulaRoot.getContext().getResources().getString(parser.errorId),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Procedure sets the error data into the term
     */
    public void setError(String errorMsg, ErrorNotification errorNotification, CustomLayout parentLayout)
    {
        this.errorId = NO_ERROR_ID;
        this.errorMsg = errorMsg;
        this.errorNotification = errorNotification;
        if (parentLayout != null)
        {
            parentLayout.setContentValid(true);
        }
        if (errorId != NO_ERROR_ID || errorMsg != null)
        {
            contentType = ContentType.INVALID;
            if (!disableCalculation() && errorNotification == ErrorNotification.PARENT_LAYOUT)
            {
                parentLayout.setContentValid(false);
            }
        }
        updateViewColor();
    }

    public void requestFocus()
    {
        if (text.isRequestFocusEnabled())
        {
            if (!setEditableFocus(FormulaBase.FocusType.FIRST_EMPTY))
            {
                setEditableFocus(FormulaBase.FocusType.FIRST_EDITABLE);
            }
        }
    }

    /**
     * Check whether this term is enabled for the given palette
     */
    public boolean isEnabledInPalette(Category pt)
    {
        if (isTerm() && term instanceof Intervals && pt != Category.NEW_TERM)
        {
            return false;
        }
        switch (pt)
        {
        case NEW_TERM:
            return parentFormula.isNewTermEnabled() && text.isNewTermEnabled();
        case TOP_LEVEL_TERM:
            return text.isIntervalEnabled() || text.isFileOperationEnabled();
        case CONVERSION:
            return text.isConversionEnabled();
        case INDEX:
            return isEquationName() || text.isConversionEnabled();
        case COMPARATOR:
            return text.isComparatorEnabled();
        }
        return false;
    }

    /**
     * Check whether this term depends on given equation
     */
    public boolean dependsOn(Equation e)
    {
        if (isTerm())
        {
            return term.dependsOn(e);
        }
        else if (contentType == ContentType.VARIABLE_LINK && linkedVariable != null
                && linkedVariable instanceof Equation)
        {
            return linkedVariable.getId() == e.getId();
        }
        return false;
    }

    /**
     * Procedure search an owner argument holder that defines (holds) the given argument
     */
    public ArgumentHolderIf findArgumentHolder(String argumentName)
    {
        FormulaBase parent = getParentFormula();
        while (parent != null)
        {
            if (parent instanceof ArgumentHolderIf)
            {
                ArgumentHolderIf argHolder = (ArgumentHolderIf) parent;
                if (argHolder.getArgumentIndex(argumentName) != ViewUtils.INVALID_INDEX)
                {
                    return argHolder;
                }
            }
            if (parent.getParentField() != null)
            {
                parent = parent.getParentField().getParentFormula();
            }
            else
            {
                break;
            }
        }
        return null;
    }

    /**
     * Procedure search an externally set error in the parent terms
     */
    private String findErrorMsg()
    {
        if (errorMsg != null)
        {
            return errorMsg;
        }
        FormulaBase parent = getParentFormula();
        while (parent != null)
        {
            final TermField tf = parent.getParentField();
            if (tf != null)
            {
                if (tf.errorMsg != null)
                {
                    return tf.errorMsg;
                }
                parent = tf.getParentFormula();
            }
            else
            {
                break;
            }
        }
        return null;
    }

    /**
     * Procedure divides the given source string into two targets using the given divider
     */
    public static void divideString(String src, String div, TermField leftTarget, TermField rightTarget)
    {
        if (div != null && src != null)
        {
            int opPosition = src.indexOf(div);
            if (opPosition >= 0)
            {
                try
                {
                    leftTarget.setText(src.subSequence(0, opPosition));
                    rightTarget.setText(src.subSequence(opPosition + div.length(), src.length()));
                }
                catch (Exception ex)
                {
                    // nothing to do
                }
            }
        }
    }

    public boolean isEquationName()
    {
        return text.isEquationName();
    }

    UserFunctions getIndexTerm()
    {
        if (isEquationName() &&
                isTerm() &&
                !getTerm().isEmpty() &&
                !getTerm().getTerms().isEmpty() &&
                getTerm() instanceof UserFunctions)
        {
            final UserFunctions f = (UserFunctions) getTerm();
            if (f.getFunctionType() == UserFunctions.FunctionType.FUNCTION_INDEX)
            {
                return f;
            }
        }
        return null;
    }

    public static boolean isArrayTerm(final String code)
    {
        return ArrayFunctions.FunctionType.MATRIX.lowerCaseName.equals(code);
    }

    boolean isArray()
    {
        return isTerm() && getTerm() instanceof ArrayFunctions && ((ArrayFunctions) getTerm()).isArray();
    }

    MatrixProperties getArrayDimension()
    {
        return isArray() ? ((ArrayFunctions) getTerm()).getArrayDimension() : null;
    }
}
