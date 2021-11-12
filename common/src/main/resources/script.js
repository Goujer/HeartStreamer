var busy = false
setInterval(function() {
	if (document.readyState === "complete" && busy == false) {
		busy = true
		fetch('/hr', {method: 'GET'})
		.then(response => {
			response.text().then(text => {
				//console.log(text)
				document.getElementById("bpm").innerHTML = text
			})
		})
		.catch(error => {
			//console.log(error)
		});
		busy = false
	}
}, 300)