// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.time.LocalDate;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * When an act applies, as its dates in Cellar tell. {@link DatedAct#applying} gives it. Each kind
 * keeps the dates that tell it, so a page can name the clauses that set them.
 */
@ValueObject
public sealed interface Applying {

  /** Returns the dates that tell when the act applies. */
  List<ActDate> dates();

  /**
   * The act applies from one day.
   *
   * @param day the day
   * @param entryIntoForce whether the day is only its entry into force
   * @param dates its dates on that day, or the dates that tell it
   */
  record From(LocalDate day, boolean entryIntoForce, List<ActDate> dates) implements Applying {
    public From {
      dates = List.copyOf(dates);
    }
  }

  /**
   * The act applies from more than one day, and Cellar does not tell which part a provision is in.
   *
   * @param starts each day from which a part applies, in order
   * @param first the first day from which a part of it applies. Its entry into force is a part, but
   *     a part applies from it only if no later day is for the whole act.
   * @param dates its dates that have a day
   */
  record FromMoreThanOneDay(List<Start> starts, LocalDate first, List<ActDate> dates)
      implements Applying {
    public FromMoreThanOneDay {
      starts = List.copyOf(starts);
      dates = List.copyOf(dates);
    }
  }

  /**
   * A day on which a part starts.
   *
   * @param day the day
   * @param entryIntoForce whether the day is only its entry into force
   * @param forPart whether each of its dates on that day is for a part
   */
  record Start(LocalDate day, boolean entryIntoForce, boolean forPart) {}

  /**
   * The act applies from a day that Cellar does not know. Its clauses set the day, for example by
   * an event.
   *
   * @param dates its dates without a day
   */
  record SetBy(List<ActDate> dates) implements Applying {
    public SetBy {
      dates = List.copyOf(dates);
    }
  }

  /**
   * The act is in force, and Cellar does not tell from when it applies. For example, a directive
   * that has only its entry into force: the Member States apply it through their own measures.
   *
   * @param day its entry into force
   * @param dates its dates
   */
  record InForce(LocalDate day, List<ActDate> dates) implements Applying {
    public InForce {
      dates = List.copyOf(dates);
    }
  }
}
