# Herramientas para mapas de aventura

Casi todos los mapas de aventura están hechos para **un jugador**: sus bloques de comandos usan `@a` ("todos los
jugadores del server"). En un server con lobby y varios mapas, eso hace que cuando uno aprieta el botón de inicio se
teletransporte a todos, que un checkpoint le cambie el punto de reaparición a todos o que se les borren ítems.

1. Listar los bloques de comandos de un mundo (con su tipo):

       python3 comandos.py nbt.py <mundo>/dimensions/minecraft/<dimensión>/region > cmds.tsv

2. Generar los comandos de consola que los reescriben:

       python3 arreglar-arroba-a.py minecraft:<dimensión> individual cmds.tsv > arreglo.txt   # @a → @p (parkour: cada uno lo suyo)
       python3 arreglar-arroba-a.py minecraft:<dimensión> coop cmds.tsv > arreglo.txt         # @a → todos, pero solo los de ese mapa

3. Pegar `arreglo.txt` línea por línea en la consola del server (hacé un respaldo del mundo antes).

**Ojo con `@p` en mapas individuales:** elige al más cercano *al bloque de comandos*, que suele estar escondido lejos
del botón. Para esos mapas poné `individual: true` en el mundo de `plugins/ClaudiaMC/config.yml`: el plugin anota quién
apretó el botón, la palanca o la placa y le aplica a esa persona los `@a`/`@p` de los bloques que se ejecutan en los
4 segundos siguientes.
