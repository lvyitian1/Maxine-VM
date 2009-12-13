/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.ins.gui;

import javax.swing.*;

import com.sun.max.ins.*;
import com.sun.max.tele.*;

/**
 * A tool bar specialized for use in the Maxine Inspector.
 *
 * @author Michael Van De Vanter
 */
public class InspectorToolBar extends JToolBar implements InspectionHolder {

    private final Inspection inspection;
    private final String tracePrefix;

    /**
     * Creates a new {@JToolBar}, specialized for use in the Maxine Inspector,
     * not floatable by default.
     */
    public InspectorToolBar(Inspection inspection) {
        this.inspection = inspection;
        this.tracePrefix = "[" + getClass().getSimpleName() + "] ";
        setFloatable(false);
        setOpaque(true);
    }

    public final Inspection inspection() {
        return inspection;
    }

    public MaxVM maxVM() {
        return inspection.maxVM();
    }

    public final MaxVMState maxVMState() {
        return inspection.maxVM().maxVMState();
    }

    public InspectorGUI gui() {
        return inspection.gui();
    }

    public final InspectorStyle style() {
        return inspection.style();
    }

    public final InspectionFocus focus() {
        return inspection.focus();
    }

    public InspectionActions actions() {
        return inspection.actions();
    }

    public void redisplay() {
    }

    public void refresh(boolean force) {
    }

    /**
     * @return default prefix text for trace messages; identifies the class being traced.
     */
    protected String tracePrefix() {
        return tracePrefix;
    }
}
