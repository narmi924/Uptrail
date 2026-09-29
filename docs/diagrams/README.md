# Diagrams

PlantUML sources (`*.puml`) and the compiled SVG files (`svg/`). The SVG files are committed so they can be read without any tools.

| Diagram | Source | Compiled |
| --- | --- | --- |
| Components and call rules | [architecture.puml](architecture.puml) | [svg/architecture.svg](svg/architecture.svg) |
| Data model (17 tables) | [data-model.puml](data-model.puml) | [svg/data-model.svg](svg/data-model.svg) |
| Course application states | [application-states.puml](application-states.puml) | [svg/application-states.svg](svg/application-states.svg) |
| Fee claim states | [claim-states.puml](claim-states.puml) | [svg/claim-states.svg](svg/claim-states.svg) |
| Training calendar year states | [calendar-year-states.puml](calendar-year-states.puml) | [svg/calendar-year-states.svg](svg/calendar-year-states.svg) |
| Submitting an application | [submit-sequence.puml](submit-sequence.puml) | [svg/submit-sequence.svg](svg/submit-sequence.svg) |

To compile after a change (no local PlantUML or Graphviz needed; the MIT-licensed PlantUML jar is downloaded by Maven on first use and the diagrams use its built-in layout engine):

```bash
./mvnw -Pdiagrams generate-resources
```

When code and a diagram disagree, the code is right; update the diagram in the same change.
