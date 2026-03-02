package com.example.projekt1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.projekt1.ui.theme.Projekt1Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Projekt1Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column (
                        modifier = Modifier.padding(innerPadding)
                    ){
                        Text("Wybierz frakcję")
                        Row() {
                            Image(
                                painter = painterResource(R.drawable.polandia),
                                contentDescription = "Polandia",
                                alignment = Alignment.CenterEnd,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        //
                                    }
                            )
                            Spacer(Modifier.padding(8.dp))
                            Image(
                                painter = painterResource(R.drawable.afrykania),
                                contentDescription = "Afrykania",
                                alignment = Alignment.CenterStart,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {

                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
