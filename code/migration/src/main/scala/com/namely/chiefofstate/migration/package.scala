/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

package object migration {
  implicit class StringImprovements(s: String) {

    /**
     * quotes a given string
     *
     * @return quoted string
     */
    def quote: String = {
      s"""\"$s\""""
    }

    /**
     * checks whether a given string  is in UPPER case or not
     *
     * @return true when it is uppercase and false when not
     */
    def isUpper: Boolean = {
      s.toCharArray.forall(c => {
        if (!c.isLetter) true
        else c.isUpper
      })
    }
  }
}
