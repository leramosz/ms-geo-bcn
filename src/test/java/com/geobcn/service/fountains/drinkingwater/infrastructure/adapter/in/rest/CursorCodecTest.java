package com.geobcn.service.fountains.drinkingwater.infrastructure.adapter.in.rest;

import com.geobcn.service.fountains.drinkingwater.domain.Cursor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorCodecTest {

    @Test
    void roundTrip() {
        for (Cursor c : new Cursor[]{new Cursor(168, "01-004", false), new Cursor(null, "10-461", false),
                new Cursor(3, "02-001", true)}) {
            assertThat(CursorCodec.decode(CursorCodec.encode(c))).isEqualTo(c);
        }
    }

    @Test
    void matchesSpecExample() {
        assertThat(CursorCodec.encode(new Cursor(168, "01-004", false))).isEqualTo("eyJkIjoxNjgsImlkIjoiMDEtMDA0In0");
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> CursorCodec.decode("%%%")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CursorCodec.decode("e30")).isInstanceOf(IllegalArgumentException.class); // {}
    }
}
