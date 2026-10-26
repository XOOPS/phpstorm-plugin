<style>a{color:red} .x {display:none}</style>
<{if $x}>
<{$y}>
<p><{$z|escape}></p>
<{* {if $hidden} *}>
<{literal}>{if}<{/literal}>
<script>if (a) { b(); }</script>
<script>function f(){if(x){run();}}</script>
<p>{if(x)}</p>
<input type="checkbox" <{if $on}>checked<{/if}>>>
