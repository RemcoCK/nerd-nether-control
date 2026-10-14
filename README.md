# Nerd End Control — Fabric 26.3

Server-side Fabric mod for Minecraft 26.3.

## Commands

In-game als OP:

```text
/end status
/end open
/end close
/end schedule 2026-10-20 20:00
/end cancel
```

In de hosting-console zonder `/`:

```text
end status
end open
end close
end schedule 2026-10-20 20:00
end cancel
```

## Gedrag

- Eerste installatie: The End is standaard gesloten.
- `open`: direct open, planning wordt geannuleerd.
- `close`: direct dicht; bestaande planning blijft staan.
- `schedule`: sluit direct en plant opening in Europe/Amsterdam.
- Nogmaals `schedule` uitvoeren vervangt de oude datum/tijd.
- `cancel`: annuleert alleen de planning.
- Spelers die al in The End zitten kunnen via het exit-portaal terug.
- Config staat in `config/nerd-end-control.json`.

## Build

Minecraft 26.3 / Fabric Loader 0.19.5+ / Fabric API / Java 25.

GitHub Actions bouwt automatisch. Gebruik daarna:

```text
nerd-end-control-1.0.0.jar
```

Niet de `-sources.jar`.
