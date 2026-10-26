<input name="op" value="<{$smarty.get.op}>">
<input name="id" value="<{$smarty.get.id|escape}>">
<p><{$smarty.request.q|default:''}></p>
<script>var q = <{$smarty.get.q|json_encode}>;</script>
