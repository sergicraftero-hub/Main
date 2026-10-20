package com.sergicraftero.escaperoom.room;

import com.sergicraftero.escaperoom.action.ActionList;
import com.sergicraftero.escaperoom.util.Cuboid;

/** Actions fired once per game when a player first enters a sub-region (e.g. a new chamber). */
public record Trigger(String id, Cuboid region, ActionList actions) {
}
